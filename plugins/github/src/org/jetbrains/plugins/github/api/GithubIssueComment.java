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

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssueComment {
  private final long myId;

  @NotNull private final String myHtmlUrl;
  @NotNull private final String myBodyHtml;

  @NotNull private final Date myCreatedAt;
  @NotNull private final Date myUpdatedAt;

  @NotNull private final GithubUser myUser;

  public GithubIssueComment(long id,
                            @NotNull String htmlUrl,
                            @NotNull String bodyHtml,
                            @NotNull Date createdAt,
                            @NotNull Date updatedAt,
                            @NotNull GithubUser user) {
    myId = id;
    myHtmlUrl = htmlUrl;
    myBodyHtml = bodyHtml;
    myCreatedAt = createdAt;
    myUpdatedAt = updatedAt;
    myUser = user;
  }

  public long getId() {
    return myId;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public String getBodyHtml() {
    return myBodyHtml;
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
