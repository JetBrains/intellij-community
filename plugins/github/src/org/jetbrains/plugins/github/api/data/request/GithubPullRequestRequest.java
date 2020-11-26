// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestRequest {
  @NotNull private final String title;
  @NotNull private final String body;
  @NotNull private final String head; // branch with changes
  @NotNull private final String base; // branch requested to
  private final boolean draft;

  public GithubPullRequestRequest(@NotNull String title,
                                  @NotNull String description,
                                  @NotNull String head,
                                  @NotNull String base,
                                  boolean isDraft) {
    this.title = title;
    this.body = description;
    this.head = head;
    this.base = base;
    this.draft = isDraft;
  }
}
