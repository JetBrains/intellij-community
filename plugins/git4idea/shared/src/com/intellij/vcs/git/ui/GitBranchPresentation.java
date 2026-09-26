// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class GitBranchPresentation {
  public static final int BRANCH_NAME_LENGTH_DELTA = 4;
  public static final int BRANCH_NAME_SUFFIX_LENGTH = 5;

  private static final int DEFAULT_MAX_BRANCH_NAME_LENGTH = 80;

  public static @NlsSafe @NotNull String truncateBranchName(@NotNull Project project,
                                                            @NotNull @NlsSafe String branchName) {
    return truncateBranchName(project, branchName, DEFAULT_MAX_BRANCH_NAME_LENGTH);
  }

  public static @NlsSafe @NotNull String truncateBranchName(@NotNull Project project,
                                                            @NotNull @NlsSafe String branchName,
                                                            int maxBranchNameLength) {
    return truncateBranchName(project, branchName, maxBranchNameLength, BRANCH_NAME_SUFFIX_LENGTH, BRANCH_NAME_LENGTH_DELTA);
  }

  public static @NlsSafe @NotNull String truncateBranchName(@NotNull Project project, @NotNull @NlsSafe String branchName,
                                                            int maxBranchNameLength, int suffixLength, int delta) {
    int branchNameLength = branchName.length();

    if (branchNameLength <= maxBranchNameLength + delta) {
      return branchName;
    }

    IssueNavigationConfiguration issueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    List<IssueNavigationConfiguration.LinkMatch> issueMatches = issueNavigationConfiguration.findIssueLinks(branchName);
    int affectedMaxBranchNameLength = maxBranchNameLength - StringUtil.ELLIPSIS.length();
    if (!issueMatches.isEmpty()) {
      // never truncate the first occurrence of the issue id
      IssueNavigationConfiguration.LinkMatch firstMatch = issueMatches.get(0);
      TextRange firstMatchRange = firstMatch.getRange();
      return truncateAndSaveIssueId(firstMatchRange, branchName, affectedMaxBranchNameLength, suffixLength, delta);
    }

    if (affectedMaxBranchNameLength - suffixLength - StringUtil.ELLIPSIS.length() < 0) {
      return branchName;
    }

    return StringUtil.shortenTextWithEllipsis(branchName, affectedMaxBranchNameLength, suffixLength, true);
  }

  private static @NlsSafe @NotNull String truncateAndSaveIssueId(@NotNull TextRange issueIdRange,
                                                                 @NotNull String branchName,
                                                                 int maxBranchNameLength,
                                                                 int suffixLength, int delta) {
    String truncatedByDefault = StringUtil.shortenTextWithEllipsis(branchName,
                                                                   maxBranchNameLength,
                                                                   suffixLength, true);
    String issueId = issueIdRange.substring(branchName);

    if (truncatedByDefault.contains(issueId)) return truncatedByDefault;

    try {
      int branchNameLength = branchName.length();
      int endOffset = issueIdRange.getEndOffset();
      int startOffset = issueIdRange.getStartOffset();

      // suffix intersects with the issue id
      if (endOffset >= branchNameLength - suffixLength - delta) {
        return StringUtil.shortenTextWithEllipsis(branchName,
                                                  maxBranchNameLength,
                                                  branchNameLength - startOffset, true);
      }

      String suffix = branchName.substring(branchNameLength - suffixLength);
      int prefixLength = maxBranchNameLength - suffixLength - issueId.length();

      String prefixAndIssue;
      if (Math.abs(startOffset - prefixLength) <= delta) {
        prefixAndIssue = branchName.substring(0, endOffset);
      }
      else {
        String prefix = branchName.substring(0, prefixLength);
        prefixAndIssue = prefix + StringUtil.ELLIPSIS + issueId;
      }

      return prefixAndIssue + StringUtil.ELLIPSIS + suffix;
    }
    catch (Throwable e) {
      return truncatedByDefault;
    }
  }
}
