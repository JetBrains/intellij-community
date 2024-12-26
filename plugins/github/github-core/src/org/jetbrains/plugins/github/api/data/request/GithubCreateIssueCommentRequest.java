// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubCreateIssueCommentRequest {
  private final @NotNull String body;

  public GithubCreateIssueCommentRequest(@NotNull String body) {
    this.body = body;
  }
}
