package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter {

  @NotNull private final Collection<String> myBranchNames;

  public VcsLogBranchFilterImpl(@NotNull final Collection<String> branchNames) {
    myBranchNames = branchNames;
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
