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
public class GithubIssue {
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
  @SuppressWarnings("ConstantConditions")
  public static GithubIssue create(@Nullable GithubIssueRaw raw) throws JsonException {
    try {
      return new GithubIssue(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubIssue parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubIssue parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubIssue(@NotNull GithubIssueRaw raw) throws JsonException {
    this(raw.htmlUrl, raw.number, raw.state, raw.title, raw.body, raw.user, raw.assignee, raw.closedAt, raw.createdAt, raw.updatedAt);
  }

  private GithubIssue(@NotNull String htmlUrl,
                      long number,
                      @NotNull String state,
                      @NotNull String title,
                      @NotNull String body,
                      @NotNull GithubUserRaw user,
                      @Nullable GithubUserRaw assignee,
                      @Nullable Date closedAt,
                      @NotNull Date createdAt,
                      @NotNull Date updatedAt) throws JsonException {
    myHtmlUrl = htmlUrl;
    myNumber = number;
    myState = state;
    myTitle = title;
    myBody = body;
    myClosedAt = closedAt;
    myCreatedAt = createdAt;
    myUpdatedAt = updatedAt;

    myUser = GithubUser.create(user);
    myAssignee = assignee == null ? null : GithubUser.create(assignee);
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
