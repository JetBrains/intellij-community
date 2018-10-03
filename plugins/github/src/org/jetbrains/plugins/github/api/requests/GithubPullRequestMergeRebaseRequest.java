// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.requests;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestMergeRebaseRequest {
  @NotNull private final String sha;
  @NotNull private final GithubPullRequestMergeMethod method;

  public GithubPullRequestMergeRebaseRequest(@NotNull String sha) {
    this.sha = sha;
    this.method = GithubPullRequestMergeMethod.rebase;
  }
}
