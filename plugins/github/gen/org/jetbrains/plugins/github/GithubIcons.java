// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class GithubIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, GithubIcons.class.getClassLoader(), cacheKey, flags);
  }
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, GithubIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon GithubWarning = load("org/jetbrains/plugins/github/githubWarning.svg", 1935282329, 2);
  /** 16x16 */ public static final @NotNull Icon IssueClosed = load("org/jetbrains/plugins/github/issueClosed.svg", 1057810441, 0);
  /** 16x16 */ public static final @NotNull Icon IssueOpened = load("org/jetbrains/plugins/github/issueOpened.svg", 1115233377, 0);
  /** 16x16 */ public static final @NotNull Icon LocalBranch = load("org/jetbrains/plugins/github/localBranch.svg", -549668610, 2);
  /** 16x16 */ public static final @NotNull Icon PullRequestDraft = load("org/jetbrains/plugins/github/pullRequestDraft.svg", -1818110574, 0);
  /** 16x16 */ public static final @NotNull Icon PullRequestMerged = load("org/jetbrains/plugins/github/pullRequestMerged.svg", -1216177352, 0);
  /** 13x13 */ public static final @NotNull Icon PullRequestsToolWindow = load("org/jetbrains/plugins/github/expui/pullRequests.svg", "org/jetbrains/plugins/github/pullRequestsToolWindow.svg", -679752252, 2);
  /** 16x16 */ public static final @NotNull Icon Review = load("org/jetbrains/plugins/github/review.svg", -1588546357, 0);
  /** 16x16 */ public static final @NotNull Icon ReviewAccepted = load("org/jetbrains/plugins/github/reviewAccepted.svg", 536493489, 0);
  /** 16x16 */ public static final @NotNull Icon ReviewRejected = load("org/jetbrains/plugins/github/reviewRejected.svg", 108213108, 0);
  /** 16x16 */ public static final @NotNull Icon Timeline = load("org/jetbrains/plugins/github/timeline.svg", 425956960, 0);
}
