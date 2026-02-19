// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestMergeRequest {
  private final @NotNull String commitTitle;
  private final @NotNull String commitMessage;
  private final @NotNull String sha;
  private final @NotNull GithubPullRequestMergeMethod mergeMethod;

  public GithubPullRequestMergeRequest(@NotNull String commitTitle,
                                       @NotNull String commitMessage,
                                       @NotNull String sha,
                                       @NotNull GithubPullRequestMergeMethod mergeMethod) {
    if (mergeMethod != GithubPullRequestMergeMethod.merge && mergeMethod != GithubPullRequestMergeMethod.squash) {
      throw new IllegalArgumentException("Invalid merge method");
    }

    this.commitTitle = commitTitle;
    this.commitMessage = commitMessage;
    this.sha = sha;
    this.mergeMethod = mergeMethod;
  }
}
