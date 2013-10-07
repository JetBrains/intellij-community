package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class RefsModel {

  @NotNull private final Collection<VcsRef> myRefs;
  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final Set<Hash> myRefHashes;

  public RefsModel(@NotNull Collection<VcsRef> allRefs) {
    myRefs = allRefs;

    myBranches = ContainerUtil.filter(myRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });

    myRefHashes = new HashSet<Hash>();
    for (VcsRef ref : myRefs) {
      myRefHashes.add(ref.getCommitHash());
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
    if (myRefHashes.contains(hash)) {
      for (VcsRef ref : myRefs) {
        if (ref.getCommitHash().equals(hash)) {
          refs.add(ref);
        }
      }
    }
    return refs;
  }

  @NotNull
  public Collection<VcsRef> getAllRefs() {
    return Collections.unmodifiableCollection(myRefs);
  }

  @NotNull
  public Collection<VcsRef> getBranches() {
    return myBranches;
  }
}
