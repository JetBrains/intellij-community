// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestMergeRequest {
  @NotNull private final String commitTitle;
  @NotNull private final String commitMessage;
  @NotNull private final String sha;
  @NotNull private final GithubPullRequestMergeMethod mergeMethod;

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
