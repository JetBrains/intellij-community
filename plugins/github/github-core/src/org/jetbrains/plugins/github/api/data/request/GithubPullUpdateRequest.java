// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GithubIssueState;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullUpdateRequest {
  private final @Nullable String title;
  private final @Nullable String body;
  private final @Nullable GithubIssueState state;
  private final @Nullable String base;
  private final @Nullable Boolean maintainerCanModify;

  public GithubPullUpdateRequest(@Nullable String title,
                                 @Nullable String body,
                                 @Nullable GithubIssueState state,
                                 @Nullable String base,
                                 @Nullable Boolean maintainerCanModify) {
    this.title = title;
    this.body = body;
    this.state = state;
    this.base = base;
    this.maintainerCanModify = maintainerCanModify;
  }
}
