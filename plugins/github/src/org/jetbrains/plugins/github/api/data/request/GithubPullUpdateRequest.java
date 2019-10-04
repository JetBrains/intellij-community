// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GithubIssueState;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullUpdateRequest {
  @Nullable private final String title;
  @Nullable private final String body;
  @Nullable private final GithubIssueState state;
  @Nullable private final String base;
  @Nullable private final Boolean maintainerCanModify;

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
