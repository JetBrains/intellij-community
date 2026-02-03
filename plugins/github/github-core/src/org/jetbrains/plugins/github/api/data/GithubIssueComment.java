// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

@SuppressWarnings("UnusedDeclaration")
public class GithubIssueComment {
  private Long id;

  private String url;
  private String htmlUrl;
  private String body;

  private Date createdAt;
  private Date updatedAt;

  private GithubUser user;

  public long getId() {
    return id;
  }

  public @NotNull String getHtmlUrl() {
    return htmlUrl;
  }

  public @NotNull Date getCreatedAt() {
    return createdAt;
  }

  public @NotNull Date getUpdatedAt() {
    return updatedAt;
  }

  public @NotNull GithubUser getUser() {
    return user;
  }
}
