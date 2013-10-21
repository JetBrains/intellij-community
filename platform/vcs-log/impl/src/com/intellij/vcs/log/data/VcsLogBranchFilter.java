package com.intellij.vcs.log.data;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * TODO use VcsRef instead of String
 */
public class VcsLogBranchFilter implements VcsLogGraphFilter {

  @NotNull private final Collection<Hash> myMatchingHeads;
  private final String myBranchName;

  public VcsLogBranchFilter(Collection<VcsRef> allRefs, final String branchName) {
    myBranchName = branchName;
    myMatchingHeads = ContainerUtil.mapNotNull(allRefs, new Function<VcsRef, Hash>() {
      @Override
      public Hash fun(VcsRef ref) {
        if (ref.getName().equals(branchName)) {
          return ref.getCommitHash();
        }
        return null;
      }
    });

  }

  @Override
  public boolean matches(@NotNull Hash hash) {
    return myMatchingHeads.contains(hash);
  }

  @Override
  public String toString() {
    return "on: " + myBranchName;
  }

}
