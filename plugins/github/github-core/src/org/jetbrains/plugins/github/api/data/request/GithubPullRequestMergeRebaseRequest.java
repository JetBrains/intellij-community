// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestMergeRebaseRequest {
  private final @NotNull String sha;
  private final @NotNull GithubPullRequestMergeMethod mergeMethod;

  public GithubPullRequestMergeRebaseRequest(@NotNull String sha) {
    this.sha = sha;
    this.mergeMethod = GithubPullRequestMergeMethod.rebase;
  }
}
