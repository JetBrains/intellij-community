// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestRequest {
  private final @NotNull String title;
  private final @NotNull String body;
  private final @NotNull String head; // branch with changes
  private final @NotNull String base; // branch requested to

  public GithubPullRequestRequest(@NotNull String title, @NotNull String description, @NotNull String head, @NotNull String base) {
    this.title = title;
    this.body = description;
    this.head = head;
    this.base = base;
  }
}
