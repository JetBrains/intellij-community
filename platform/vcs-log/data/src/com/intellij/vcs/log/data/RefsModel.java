package com.intellij.vcs.log.data;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class RefsModel {
  private final Collection<VcsRef> allRefs;
  private final Set<Hash> trackedCommitHashes = new HashSet<Hash>();

  public RefsModel(Collection<VcsRef> allRefs) {
    this.allRefs = allRefs;
    computeTrackedCommitHash();
  }

  private void computeTrackedCommitHash() {
    for (VcsRef ref : allRefs) {
      trackedCommitHashes.add(ref.getCommitHash());
    }
  }

  public boolean isBranchRef(@NotNull Hash commitHash) {
    for (VcsRef ref : refsToCommit(commitHash)) {
      if (ref.getType().isBranch()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public List<VcsRef> refsToCommit(@NotNull Hash hash) {
    List<VcsRef> refs = new ArrayList<VcsRef>();
    if (trackedCommitHashes.contains(hash)) {
      for (VcsRef ref : allRefs) {
        if (ref.getCommitHash().equals(hash)) {
          refs.add(ref);
        }
      }
    }
    return refs;
  }

  @NotNull
  public Set<Hash> getTrackedCommitHashes() {
    return Collections.unmodifiableSet(trackedCommitHashes);
  }

  @NotNull
  public Collection<VcsRef> getAllRefs() {
    return Collections.unmodifiableCollection(allRefs);
  }


}
