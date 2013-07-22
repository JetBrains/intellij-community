/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubIssue {
  @NotNull private String url;
  @NotNull private String htmlUrl;
  private long number;
  @NotNull private String state;
  @NotNull private String title;
  @NotNull private String body;

  @NotNull private GithubUser user;
  @Nullable private GithubUser assignee;

  @Nullable Date closedAt;
  @NotNull Date createdAt;
  @NotNull Date updatedAt;

  @NotNull
  public static GithubIssue create(@Nullable GithubIssueRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.url == null) throw new JsonException("url is null");
      if (raw.htmlUrl == null) throw new JsonException("htmlUrl is null");
      if (raw.number == null) throw new JsonException("number is null");
      if (raw.state == null) throw new JsonException("state is null");
      if (raw.title == null) throw new JsonException("title is null");
      if (raw.body == null) throw new JsonException("body is null");

      if (raw.createdAt == null) throw new JsonException("createdAt is null");
      if (raw.updatedAt == null) throw new JsonException("updatedAt is null");

      GithubUser user = GithubUser.create(raw.user);
      GithubUser assignee = raw.assignee == null ? null : GithubUser.create(raw.assignee);

      return new GithubIssue(raw.url, raw.htmlUrl, raw.number, raw.state, raw.title, raw.body, user, assignee, raw.closedAt, raw.createdAt,
                             raw.updatedAt);
    }
    catch (JsonException e) {
      throw new JsonException("GithubIssue parse error", e);
    }
  }

  private GithubIssue(@NotNull String url,
                      @NotNull String htmlUrl,
                      long number,
                      @NotNull String state,
                      @NotNull String title,
                      @NotNull String body,
                      @NotNull GithubUser user,
                      @Nullable GithubUser assignee,
                      @Nullable Date closedAt,
                      @NotNull Date createdAt,
                      @NotNull Date updatedAt) {
    this.url = url;
    this.htmlUrl = htmlUrl;
    this.number = number;
    this.state = state;
    this.title = title;
    this.body = body;
    this.user = user;
    this.assignee = assignee;
    this.closedAt = closedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  public long getNumber() {
    return number;
  }

  @NotNull
  public String getState() {
    return state;
  }

  @NotNull
  public String getTitle() {
    return title;
  }

  @NotNull
  public String getBody() {
    return body;
  }

  @NotNull
  public GithubUser getUser() {
    return user;
  }

  @Nullable
  public GithubUser getAssignee() {
    return assignee;
  }

  @Nullable
  public Date getClosedAt() {
    return closedAt;
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
