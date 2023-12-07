// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @see VcsLogFilterObject#fromBranch(String)
 * @see VcsLogFilterObject#fromBranchPatterns
 */
class VcsLogBranchFilterImpl implements VcsLogBranchFilter {
  private final @NotNull List<String> myBranches;
  private final @NotNull List<Pattern> myPatterns;

  private final @NotNull List<String> myExcludedBranches;
  private final @NotNull List<Pattern> myExcludedPatterns;

  VcsLogBranchFilterImpl(@NotNull List<String> branches,
                         @NotNull List<Pattern> patterns,
                         @NotNull List<String> excludedBranches,
                         @NotNull List<Pattern> excludedPatterns) {
    myBranches = branches;
    myPatterns = patterns;
    myExcludedBranches = excludedBranches;
    myExcludedPatterns = excludedPatterns;
  }

  @Override
  public @NotNull Collection<String> getTextPresentation() {
    List<String> result = new ArrayList<>();

    result.addAll(myBranches);
    result.addAll(ContainerUtil.map(myPatterns, pattern -> pattern.pattern()));

    result.addAll(ContainerUtil.map(myExcludedBranches, branchName -> "-" + branchName));
    result.addAll(ContainerUtil.map(myExcludedPatterns, pattern -> "-" + pattern.pattern()));

    return result;
  }

  @Override
  public boolean isEmpty() {
    return myBranches.isEmpty()
           && myPatterns.isEmpty()
           && myExcludedBranches.isEmpty()
           && myExcludedPatterns.isEmpty();
  }

  @Override
  public @NonNls String toString() {
    String result = "";
    if (!myPatterns.isEmpty()) {
      result += "on patterns: " + StringUtil.join(myPatterns, ", ");
    }
    if (!myBranches.isEmpty()) {
      if (!result.isEmpty()) result += "; ";
      result += "on branches: " + StringUtil.join(myBranches, ", ");
    }
    if (!myExcludedPatterns.isEmpty()) {
      if (result.isEmpty()) result += "; ";
      result += "not on patterns: " + StringUtil.join(myExcludedPatterns, ", ");
    }
    if (!myExcludedBranches.isEmpty()) {
      if (result.isEmpty()) result += "; ";
      result += "not on branches: " + StringUtil.join(myExcludedBranches, ", ");
    }
    return result;
  }

  @Override
  public boolean matches(@NotNull String name) {
    return isIncluded(name) && !isExcluded(name);
  }

  private boolean isIncluded(@NotNull String name) {
    if (myPatterns.isEmpty() && myBranches.isEmpty()) return true;
    return isMatched(name, myBranches, myPatterns);
  }

  private boolean isExcluded(@NotNull String name) {
    return isMatched(name, myExcludedBranches, myExcludedPatterns);
  }

  private static boolean isMatched(@NotNull String name, @NotNull List<String> branches, @NotNull List<Pattern> patterns) {
    if (branches.contains(name)) return true;
    for (Pattern regexp : patterns) {
      if (regexp.matcher(name).matches()) return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogBranchFilterImpl filter = (VcsLogBranchFilterImpl)o;
    return Comparing.haveEqualElements(myBranches, filter.myBranches) &&
           Comparing.haveEqualElements(myPatterns, filter.myPatterns) &&
           Comparing.haveEqualElements(myExcludedBranches, filter.myExcludedBranches) &&
           Comparing.haveEqualElements(myExcludedPatterns, filter.myExcludedPatterns);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Comparing.unorderedHashcode(myBranches),
                        Comparing.unorderedHashcode(myPatterns),
                        Comparing.unorderedHashcode(myExcludedBranches),
                        Comparing.unorderedHashcode(myExcludedPatterns));
  }
}
