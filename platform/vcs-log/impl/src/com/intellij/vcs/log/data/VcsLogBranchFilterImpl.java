package com.intellij.vcs.log.data;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter {

  @NotNull private final Collection<String> myBranchNames;
  @NotNull private final Collection<String> myExcludedBranchNames;

  public VcsLogBranchFilterImpl(@NotNull final Collection<String> branchNames, @NotNull Collection<String> excludedBranchNames) {
    myBranchNames = branchNames;
    myExcludedBranchNames = excludedBranchNames;
  }

  @Override
  public String toString() {
    return !myBranchNames.isEmpty()
           ? "on: " + StringUtil.join(myBranchNames, ", ")
           : "not on: " + StringUtil.join(myExcludedBranchNames, ", ");
  }

  @Override
  @NotNull
  public Collection<String> getBranchNames() {
    return myBranchNames;
  }

  @NotNull
  @Override
  public Collection<String> getExcludedBranchNames() {
    return myExcludedBranchNames;
  }
}
