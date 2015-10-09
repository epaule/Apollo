package org.bbop.apollo

import grails.transaction.Transactional
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.Strand
import org.grails.plugins.metrics.groovy.Timed

//@GrailsCompileStatic
@Transactional
class NonCanonicalSplitSiteService {

    def featureRelationshipService
    def exonService
    def featureService
    def sequenceService

    /** Delete an non canonical 5' splice site.
     * @param transcript - associated transcript
     * @param nonCanonicalFivePrimeSpliceSite - NonCanonicalFivePrimeSpliceSite to be deleted
     */
    public void deleteNonCanonicalFivePrimeSpliceSite(Transcript transcript, NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {
        featureRelationshipService.deleteChildrenForTypes(transcript,NonCanonicalFivePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalFivePrimeSpliceSite,Transcript.ontologyId)
        nonCanonicalFivePrimeSpliceSite.delete()
    }

    public void deleteNonCanonicalThreePrimeSpliceSite(Transcript transcript, NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {

        featureRelationshipService.deleteChildrenForTypes(transcript,NonCanonicalThreePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalThreePrimeSpliceSite,Transcript.ontologyId)
        nonCanonicalThreePrimeSpliceSite.delete()
    }

    /** Delete all non canonical 5' splice site.
     */
    public void deleteAllNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            deleteNonCanonicalFivePrimeSpliceSite(transcript,spliceSite);
        }
    }

    /** Retrieve all the non canonical 5' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 5' splice sites.  Non canonical 5' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 5' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 5' splice sites associated with this transcript
     */
    public Collection<NonCanonicalFivePrimeSpliceSite> getNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        return (Collection<NonCanonicalFivePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript,NonCanonicalFivePrimeSpliceSite.ontologyId)
    }

    /** Retrieve all the non canonical 3' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 3' splice sites.  Non canonical 3' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 3' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 3' splice sites associated with this transcript
     */
    public Collection<NonCanonicalThreePrimeSpliceSite> getNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
        return (Collection<NonCanonicalThreePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript,NonCanonicalThreePrimeSpliceSite.ontologyId)
    }

    /** Delete all non canonical 3' splice site.  Deletes all transcript -> non canonical 3' splice sites and
     *  non canonical 3' splice sites -> transcript relationships.
     *
     */
    public void deleteAllNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
            deleteNonCanonicalThreePrimeSpliceSite(transcript,spliceSite)
        }
    }

    @Timed
    public void findNonCanonicalAcceptorDonorSpliceSites(Transcript transcript) {

        transcript.attach()

        deleteAllNonCanonicalFivePrimeSpliceSites(transcript)
        deleteAllNonCanonicalThreePrimeSpliceSites(transcript)

        List<Exon> exons = exonService.getSortedExons(transcript)
        int fmin=transcript.getFeatureLocation().fmin
        int fmax=transcript.getFeatureLocation().fmax
        Sequence sequence=transcript.getFeatureLocation().sequence
        Strand strand=transcript.getFeatureLocation().strand==-1?Strand.NEGATIVE:Strand.POSITIVE

        String residues = sequenceService.getGenomicResiduesFromSequenceWithAlterations(sequence,fmin,fmax,strand);
        if(transcript.getStrand()==-1)residues=residues.reverse()

        List<SequenceAlteration> sequenceAlterationList = new ArrayList<>()
        sequenceAlterationList.addAll(featureService.getAllSequenceAlterationsForFeature(transcript))

        for (Exon exon : exons) {
            int fivePrimeSpliceSitePosition = -1;
            int threePrimeSpliceSitePosition = -1;
            boolean validFivePrimeSplice = false;
            boolean validThreePrimeSplice = false;
            for (String donor : SequenceTranslationHandler.getSpliceDonorSites()){
                for (String acceptor : SequenceTranslationHandler.getSpliceAcceptorSites()){
                    int local11=exon.fmin-donor.length()-transcript.fmin
                    int local22=exon.fmin-transcript.fmin
                    int local33=exon.fmax-transcript.fmin
                    int local44=exon.fmax+donor.length()-transcript.fmin

                    int local1=featureService.convertSourceToModifiedLocalCoordinate(transcript,local11,sequenceAlterationList)
                    int local2=featureService.convertSourceToModifiedLocalCoordinate(transcript,local22,sequenceAlterationList)
                    int local3=featureService.convertSourceToModifiedLocalCoordinate(transcript,local33,sequenceAlterationList)
                    int local4=featureService.convertSourceToModifiedLocalCoordinate(transcript,local44,sequenceAlterationList)


                    if (exon.featureLocation.getStrand() == -1) {
                        int tmp1=local1
                        int tmp2=local2
                        local1=local3
                        local2=local4
                        local3=tmp1
                        local4=tmp2
                    }
                    if(local1>=0&&local2 < residues.length()) {
                        String acceptorSpliceSiteSequence = residues.substring(local1,local2)
                        acceptorSpliceSiteSequence=transcript.getStrand()==-1?acceptorSpliceSiteSequence.reverse():acceptorSpliceSiteSequence
                        log.debug "acceptor ${local1} ${local2} ${acceptorSpliceSiteSequence} ${acceptor}"
                        if(acceptorSpliceSiteSequence==acceptor)
                            validThreePrimeSplice=true
                        else
                            threePrimeSpliceSitePosition = exon.getStrand() == -1 ? local1 : local2;
                    }

                    if(local3>=0&&local4<residues.length()) {
                        String donorSpliceSiteSequence = residues.substring(local3,local4)
                        donorSpliceSiteSequence=transcript.getStrand()==-1?donorSpliceSiteSequence.reverse():donorSpliceSiteSequence
                        log.debug "donor ${local3} ${local4} ${donorSpliceSiteSequence} ${donor}"
                        if(donorSpliceSiteSequence==donor)
                            validFivePrimeSplice=true
                        else
                            fivePrimeSpliceSitePosition = exon.getStrand() == -1 ? local3 : local4;
                    }
                }
            }
            if (!validFivePrimeSplice && fivePrimeSpliceSitePosition != -1) {
                def loc=fivePrimeSpliceSitePosition+transcript.fmin
                log.debug "adding a noncanonical five prime splice site at ${fivePrimeSpliceSitePosition} ${loc}"
                addNonCanonicalFivePrimeSpliceSite(transcript,createNonCanonicalFivePrimeSpliceSite(transcript, loc));
            }
            if (!validThreePrimeSplice && threePrimeSpliceSitePosition != -1) {
                def loc=threePrimeSpliceSitePosition+transcript.fmin
                log.debug "adding a noncanonical three prime splice site at ${threePrimeSpliceSitePosition} ${loc}"
                addNonCanonicalThreePrimeSpliceSite(transcript,createNonCanonicalThreePrimeSpliceSite(transcript, loc));
            }
        }

        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
    }

    /** Add a non canonical 5' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalFivePrimeSpliceSite - Non canonical 5' splice site to be added
     */
    public void addNonCanonicalFivePrimeSpliceSite(Transcript transcript,NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {

        // add non canonical 5' splice site
        FeatureRelationship fr = new FeatureRelationship(
                parentFeature: transcript
                , childFeature: nonCanonicalFivePrimeSpliceSite
        ).save();
        transcript.addToParentFeatureRelationships(fr);
        nonCanonicalFivePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    /** Add a non canonical 3' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalThreePrimeSpliceSite - Non canonical 3' splice site to be added
     */
    public void addNonCanonicalThreePrimeSpliceSite(Transcript transcript,NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {

        // add non canonical 3' splice site
        FeatureRelationship fr = new FeatureRelationship(
                parentFeature: transcript
                , childFeature: nonCanonicalThreePrimeSpliceSite
        ).save();
        transcript.addToParentFeatureRelationships(fr);
        nonCanonicalThreePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    private NonCanonicalFivePrimeSpliceSite createNonCanonicalFivePrimeSpliceSite(Transcript transcript, int position) {
        String uniqueName = transcript.getUniqueName() + "-non_canonical_five_prive_splice_site-" + position;
        NonCanonicalFivePrimeSpliceSite spliceSite = new NonCanonicalFivePrimeSpliceSite(
                uniqueName: uniqueName
                ,isAnalysis: transcript.isAnalysis
                ,isObsolete: transcript.isObsolete
                ,name: uniqueName
                ).save()
        spliceSite.addToFeatureLocations(new FeatureLocation(
                strand: transcript.strand
                ,sequence: transcript.featureLocation.sequence
                ,fmin: position
                ,fmax: position
                ,feature: spliceSite
        ).save());
        return spliceSite;
    }


    private NonCanonicalThreePrimeSpliceSite createNonCanonicalThreePrimeSpliceSite(Transcript transcript, int position) {
        String uniqueName = transcript.getUniqueName() + "-non_canonical_three_prime_splice_site-" + position;
        NonCanonicalThreePrimeSpliceSite spliceSite = new NonCanonicalThreePrimeSpliceSite(
                uniqueName: uniqueName
                ,name: uniqueName
                ,isAnalysis: transcript.isAnalysis
                ,isObsolete: transcript.isObsolete
        ).save()
        spliceSite.addToFeatureLocations(new FeatureLocation(
                strand: transcript.strand
                ,sequence: transcript.featureLocation.sequence
                ,fmin: position
                ,fmax: position
                ,feature: spliceSite
        ).save());

        return spliceSite;
    }

}
