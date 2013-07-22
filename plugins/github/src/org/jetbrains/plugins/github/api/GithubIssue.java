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
  @NotNull private String myUrl;
  @NotNull private String myHtmlUrl;
  private long myNumber;
  @NotNull private String myState;
  @NotNull private String myTitle;
  @NotNull private String myBody;

  @NotNull private GithubUser myUser;
  @Nullable private GithubUser myAssignee;

  @Nullable Date myClosedAt;
  @NotNull Date myCreatedAt;
  @NotNull Date myUpdatedAt;

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
    this.myUrl = url;
    this.myHtmlUrl = htmlUrl;
    this.myNumber = number;
    this.myState = state;
    this.myTitle = title;
    this.myBody = body;
    this.myUser = user;
    this.myAssignee = assignee;
    this.myClosedAt = closedAt;
    this.myCreatedAt = createdAt;
    this.myUpdatedAt = updatedAt;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  public long getNumber() {
    return myNumber;
  }

  @NotNull
  public String getState() {
    return myState;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public String getBody() {
    return myBody;
  }

  @NotNull
  public GithubUser getUser() {
    return myUser;
  }

  @Nullable
  public GithubUser getAssignee() {
    return myAssignee;
  }

  @Nullable
  public Date getClosedAt() {
    return myClosedAt;
  }

  @NotNull
  public Date getCreatedAt() {
    return myCreatedAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return myUpdatedAt;
  }
}
