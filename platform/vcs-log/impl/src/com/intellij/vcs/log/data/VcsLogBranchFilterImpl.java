package com.intellij.vcs.log.data;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter {
  @NotNull private final List<String> myBranches;
  @NotNull private final List<Pattern> myPatterns;

  @NotNull private final List<String> myExcludedBranches;
  @NotNull private final List<Pattern> myExcludedPatterns;

  private VcsLogBranchFilterImpl(@NotNull List<String> branches,
                                 @NotNull List<Pattern> patterns,
                                 @NotNull List<String> excludedBranches,
                                 @NotNull List<Pattern> excludedPatterns) {
    myBranches = branches;
    myPatterns = patterns;
    myExcludedBranches = excludedBranches;
    myExcludedPatterns = excludedPatterns;
  }

  @Deprecated
  public VcsLogBranchFilterImpl(@NotNull Collection<String> branches,
                                 @NotNull Collection<String> excludedBranches) {
    myBranches = new ArrayList<String>(branches);
    myPatterns = new ArrayList<Pattern>();
    myExcludedBranches = new ArrayList<String>(excludedBranches);
    myExcludedPatterns = new ArrayList<Pattern>();
  }

  @Nullable
  public static VcsLogBranchFilterImpl fromBranch(@NotNull final String branchName) {
    return new VcsLogBranchFilterImpl(Collections.singletonList(branchName),
                                      Collections.<Pattern>emptyList(),
                                      Collections.<String>emptyList(),
                                      Collections.<Pattern>emptyList());
  }

  @Nullable
  public static VcsLogBranchFilterImpl fromTextPresentation(@NotNull final Collection<String> strings) {
    if (strings.isEmpty()) return null;

    List<String> branches = new ArrayList<String>();
    List<String> excludedBranches = new ArrayList<String>();
    List<Pattern> patterns = new ArrayList<Pattern>();
    List<Pattern> excludedPatterns = new ArrayList<Pattern>();

    for (String string : strings) {
      boolean isRegexp = isRegexp(string);
      boolean isExcluded = string.startsWith("-");
      string = isExcluded ? string.substring(1) : string;

      if (isRegexp) {
        if (isExcluded) {
          excludedPatterns.add(Pattern.compile(string));
        }
        else {
          patterns.add(Pattern.compile(string));
        }
      }
      else {
        if (isExcluded) {
          excludedBranches.add(string);
        }
        else {
          branches.add(string);
        }
      }
    }

    return new VcsLogBranchFilterImpl(branches, patterns, excludedBranches, excludedPatterns);
  }

  @NotNull
  @Override
  public Collection<String> getTextPresentation() {
    List<String> result = new ArrayList<String>();

    result.addAll(myBranches);
    result.addAll(ContainerUtil.map(myPatterns, new Function<Pattern, String>() {
      @Override
      public String fun(Pattern pattern) {
        return pattern.pattern();
      }
    }));

    result.addAll(ContainerUtil.map(myExcludedBranches, new Function<String, String>() {
      @Override
      public String fun(String branchName) {
        return "-" + branchName;
      }
    }));
    result.addAll(ContainerUtil.map(myExcludedPatterns, new Function<Pattern, String>() {
      @Override
      public String fun(Pattern pattern) {
        return "-" + pattern.pattern();
      }
    }));

    return result;
  }

  @Override
  public String toString() {
    return "on patterns: " + StringUtil.join(myPatterns, ", ") + "; branches: " + StringUtil.join(myBranches, ", ");
  }

  @Override
  public boolean isShown(@NotNull String name) {
    return isIncluded(name) && !isExcluded(name);
  }

  private boolean isIncluded(@NotNull String name) {
    if (myPatterns.isEmpty() && myBranches.isEmpty()) return true;
    if (myBranches.contains(name)) return true;
    for (Pattern regexp : myPatterns) {
      if (regexp.matcher(name).matches()) return true;
    }
    return false;
  }

  private boolean isExcluded(@NotNull String name) {
    if (myExcludedBranches.contains(name)) return true;
    for (Pattern regexp : myExcludedPatterns) {
      if (regexp.matcher(name).matches()) return true;
    }
    return false;
  }

  @Nullable
  @Override
  public String getSingleFilteredBranch() {
    if (!myPatterns.isEmpty()) return null;
    if (myBranches.size() != 1) return null;
    String branch = myBranches.get(0);
    return isExcluded(branch) ? null : branch;
  }

  private static boolean isRegexp(@NotNull String pattern) {
    return StringUtil.containsAnyChar(pattern, "()[]{}.*?+^$\\|");
  }
}
