package com.intellij.vcs.log.data;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class VcsLogBranchFilter implements VcsLogGraphFilter {

  @NotNull private final Collection<Integer> myMatchingHeads;
  @NotNull private final String myBranchName;

  public VcsLogBranchFilter(@NotNull Collection<VcsRef> allRefs, @NotNull final String selectedBranchName) {
    myBranchName = selectedBranchName;
    myMatchingHeads = ContainerUtil.mapNotNull(allRefs, new Function<VcsRef, Integer>() {
      @Override
      public Integer fun(VcsRef ref) {
        if (ref.getName().equals(selectedBranchName)) {
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
    return "on: " + myBranchName;
  }

  @NotNull
  public String getBranchName() {
    return myBranchName;
  }

}
