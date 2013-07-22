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
public class GithubIssueComment {
  @NotNull private Long myId;

  @NotNull private String myHtmlUrl;
  @NotNull private String myBody;

  @NotNull private Date myCreatedAt;
  @NotNull private Date myUpdatedAt;

  @NotNull private GithubUser myUser;

  @NotNull
  @SuppressWarnings("ConstantConditions")
  public static GithubIssueComment create(@Nullable GithubIssueCommentRaw raw) throws JsonException {
    try {
      return new GithubIssueComment(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubIssueComment parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubIssueComment parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubIssueComment(@NotNull GithubIssueCommentRaw raw) throws JsonException {
    myId = raw.id;
    myHtmlUrl = raw.htmlUrl;
    myBody = raw.body;
    myCreatedAt = raw.createdAt;
    myUpdatedAt = raw.updatedAt;
    myUser = GithubUser.create(raw.user);
  }

  @NotNull
  public Long getId() {
    return myId;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public String getBody() {
    return myBody;
  }

  @NotNull
  public Date getCreatedAt() {
    return myCreatedAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return myUpdatedAt;
  }

  @NotNull
  public GithubUser getUser() {
    return myUser;
  }
}
