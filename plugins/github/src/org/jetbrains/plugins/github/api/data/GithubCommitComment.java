// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

@SuppressWarnings("UnusedDeclaration")
public class GithubCommitComment {
  private String htmlUrl;
  private String url;

  private Long id;
  private String commitId;
  private String path;
  private Long position;
  private Long line;
  private String body;
  private String bodyHtml;

  private GithubUser user;

  private Date createdAt;
  private Date updatedAt;

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  public long getId() {
    return id;
  }

  @NotNull
  public String getSha() {
    return commitId;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public long getPosition() {
    return position;
  }

  @NotNull
  public String getBodyHtml() {
    return bodyHtml;
  }

  @NotNull
  public GithubUser getUser() {
    return user;
  }

  @NotNull
  public Date getCreatedAt() {
    return createdAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return updatedAt;
  }
}
