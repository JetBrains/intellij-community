package com.intellij.vcs.log.data;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import com.intellij.vcs.log.VcsLogGraphFilter;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter, VcsLogGraphFilter {

  @NotNull private final Collection<Integer> myMatchingHeads;
  @NotNull private final Collection<String> myBranchNames;

  public VcsLogBranchFilterImpl(@NotNull Collection<VcsRef> allRefs, @NotNull final Collection<String> branchNames) {
    myBranchNames = branchNames;
    myMatchingHeads = ContainerUtil.mapNotNull(allRefs, new Function<VcsRef, Integer>() {
      @Override
      public Integer fun(VcsRef ref) {
        if (branchNames.contains(ref.getName())) {
          return ref.getCommitIndex();
        }
        return null;
      }
    });
  }

  @Override
  public boolean matches(int hash) {
    return myMatchingHeads.contains(hash);
  }

  @Override
  public String toString() {
    return "on: " + myBranchNames;
  }

  @Override
  @NotNull
  public Collection<String> getBranchNames() {
    return myBranchNames;
  }

}
