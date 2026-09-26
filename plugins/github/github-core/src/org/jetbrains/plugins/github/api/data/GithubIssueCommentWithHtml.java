// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedDeclaration")
public class GithubIssueCommentWithHtml extends GithubIssueComment {
  private String bodyHtml;

  public @NotNull String getBodyHtml() {
    return bodyHtml;
  }
}
