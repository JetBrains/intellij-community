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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssue {
  @NotNull private final String myHtmlUrl;
  private final long myNumber;
  @NotNull private final String myState;
  @NotNull private final String myTitle;
  @NotNull private final String myBody;

  @NotNull private final GithubUser myUser;
  @Nullable private final GithubUser myAssignee;

  @Nullable private final Date myClosedAt;
  @NotNull private final Date myCreatedAt;
  @NotNull private final Date myUpdatedAt;

  public GithubIssue(@NotNull String htmlUrl,
                     long number,
                     @NotNull String state,
                     @NotNull String title,
                     @Nullable String body,
                     @NotNull GithubUser user,
                     @Nullable GithubUser assignee,
                     @Nullable Date closedAt,
                     @NotNull Date createdAt,
                     @NotNull Date updatedAt) {
    myHtmlUrl = htmlUrl;
    myNumber = number;
    myState = state;
    myTitle = title;
    myBody = StringUtil.notNullize(body);
    myUser = user;
    myAssignee = assignee;
    myClosedAt = closedAt;
    myCreatedAt = createdAt;
    myUpdatedAt = updatedAt;
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
