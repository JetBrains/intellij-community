/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private Long position;
  private Long originalPosition;
  private String commitId;
  private String originalCommitId;

  private Long inReplyToId;

  @Mandatory private GithubUser user;

  private String body;

  @Mandatory private Date createdAt;
  @Mandatory private Date updatedAt;
  @Mandatory private String htmlUrl;
  @Mandatory private String pullRequestUrl;

  public GithubPullRequestComment(@NotNull GithubUser user, @NotNull Date createdAt) {
    this.user = user;
    this.createdAt = createdAt;
  }

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
  public Long getPosition() {
    return position;
  }

  @Nullable
  public Long getOriginalPosition() {
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
