// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequestComment {
  private String url;
  @Mandatory private Long id;
  @Mandatory private Long pullRequestReviewId;

  private String diffHunk;
  private String path;
  private Integer position;
  private Integer originalPosition;
  private String commitId;
  private String originalCommitId;

  private Long inReplyToId;

  @Mandatory private GithubUser user;

  private String body;

  @Mandatory private Date createdAt;
  @Mandatory private Date updatedAt;
  @Mandatory private String htmlUrl;
  @Mandatory private String pullRequestUrl;

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  public long getId() {
    return id;
  }

  @Nullable
  public String getOriginalCommitId() {
    return originalCommitId;
  }

  @Nullable
  public String getPath() {
    return path;
  }

  @Nullable
  public Integer getPosition() {
    return position;
  }

  @Nullable
  public Integer getOriginalPosition() {
    return originalPosition;
  }

  @Nullable
  public Long getInReplyToId() {
    return inReplyToId;
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
