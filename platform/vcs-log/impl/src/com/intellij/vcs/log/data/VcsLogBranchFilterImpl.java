package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class VcsLogBranchFilterImpl implements VcsLogBranchFilter {
  private static final Logger LOG = Logger.getInstance(VcsLogBranchFilterImpl.class);

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
  public VcsLogBranchFilterImpl(@NotNull Collection<String> branches, @NotNull Collection<String> excludedBranches) {
    myBranches = new ArrayList<String>(branches);
    myPatterns = new ArrayList<Pattern>();
    myExcludedBranches = new ArrayList<String>(excludedBranches);
    myExcludedPatterns = new ArrayList<Pattern>();
  }

  @Nullable
  public static VcsLogBranchFilterImpl fromBranch(@NotNull final String branchName) {
    return new VcsLogBranchFilterImpl(Collections.singletonList(branchName), Collections.<Pattern>emptyList(),
                                      Collections.<String>emptyList(), Collections.<Pattern>emptyList());
  }

  @NotNull
  public static VcsLogBranchFilterImpl fromTextPresentation(@NotNull Collection<String> strings, @NotNull Set<String> existingBranches) {
    List<String> branchNames = new ArrayList<String>();
    List<String> excludedBranches = new ArrayList<String>();
    List<Pattern> patterns = new ArrayList<Pattern>();
    List<Pattern> excludedPatterns = new ArrayList<Pattern>();

    for (String string : strings) {
      boolean isExcluded = string.startsWith("-");
      string = isExcluded ? string.substring(1) : string;
      boolean isRegexp = !existingBranches.contains(string);

      if (isRegexp) {
        try {
          Pattern pattern = Pattern.compile(string);
          if (isExcluded) {
            excludedPatterns.add(pattern);
          }
          else {
            patterns.add(pattern);
          }
        }
        catch (PatternSyntaxException e) {
          LOG.warn("Pattern " + string + " is not a proper regular expression and no branch can be found with that name.", e);
          if (isExcluded) {
            excludedBranches.add(string);
          }
          else {
            branchNames.add(string);
          }
        }
      }
      else {
        if (isExcluded) {
          excludedBranches.add(string);
        }
        else {
          branchNames.add(string);
        }
      }
    }

    return new VcsLogBranchFilterImpl(branchNames, patterns, excludedBranches, excludedPatterns);
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
}
