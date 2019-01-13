package com.intellij.vcs.log.data;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter {
  @NotNull private final List<String> myBranches;
  @NotNull private final List<Pattern> myPatterns;

  @NotNull private final List<String> myExcludedBranches;
  @NotNull private final List<Pattern> myExcludedPatterns;

  protected VcsLogBranchFilterImpl(@NotNull List<String> branches,
                                   @NotNull List<Pattern> patterns,
                                   @NotNull List<String> excludedBranches,
                                   @NotNull List<Pattern> excludedPatterns) {
    myBranches = branches;
    myPatterns = patterns;
    myExcludedBranches = excludedBranches;
    myExcludedPatterns = excludedPatterns;
  }

  /**
   * @deprecated use {@link com.intellij.vcs.log.visible.filters.VcsLogFilterObject#fromBranchPatterns(Collection, Set)} or
   * {@link com.intellij.vcs.log.visible.filters.VcsLogFilterObject#fromBranch(String)}
   */
  @Deprecated
  public VcsLogBranchFilterImpl(@NotNull Collection<String> branches, @NotNull Collection<String> excludedBranches) {
    myBranches = new ArrayList<>(branches);
    myPatterns = new ArrayList<>();
    myExcludedBranches = new ArrayList<>(excludedBranches);
    myExcludedPatterns = new ArrayList<>();
  }

  @NotNull
  @Override
  public Collection<String> getTextPresentation() {
    List<String> result = new ArrayList<>();

    result.addAll(myBranches);
    result.addAll(ContainerUtil.map(myPatterns, pattern -> pattern.pattern()));

    result.addAll(ContainerUtil.map(myExcludedBranches, branchName -> "-" + branchName));
    result.addAll(ContainerUtil.map(myExcludedPatterns, pattern -> "-" + pattern.pattern()));

    return result;
  }

  @Override
  public String toString() {
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
}
