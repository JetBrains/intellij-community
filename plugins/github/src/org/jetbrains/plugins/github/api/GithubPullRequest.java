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
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequest {
  private final long myNumber;
  @NotNull private final String myState;
  @NotNull private final String myTitle;
  @NotNull private final String myBodyHtml;

  @NotNull private final String myHtmlUrl;
  @NotNull private final String myDiffUrl;
  @NotNull private final String myPatchUrl;
  @NotNull private final String myIssueUrl;

  @NotNull private final Date myCreatedAt;
  @NotNull private final Date myUpdatedAt;
  @Nullable private final Date myClosedAt;
  @Nullable private final Date myMergedAt;

  @NotNull private final GithubUser myUser;

  @NotNull private final Link myHead;
  @NotNull private final Link myBase;

  public static class Link {
    @NotNull private final String myLabel;
    @NotNull private final String myRef;
    @NotNull private final String mySha;

    @NotNull private final GithubRepo myRepo;
    @NotNull private final GithubUser myUser;

    public Link(@NotNull String label, @NotNull String ref, @NotNull String sha, @NotNull GithubRepo repo, @NotNull GithubUser user) {
      myLabel = label;
      myRef = ref;
      mySha = sha;
      myRepo = repo;
      myUser = user;
    }

    @NotNull
    public String getLabel() {
      return myLabel;
    }

    @NotNull
    public String getRef() {
      return myRef;
    }

    @NotNull
    public String getSha() {
      return mySha;
    }

    @NotNull
    public GithubRepo getRepo() {
      return myRepo;
    }

    @NotNull
    public GithubUser getUser() {
      return myUser;
    }
  }

  public GithubPullRequest(long number,
                           @NotNull String state,
                           @NotNull String title,
                           @Nullable String bodyHtml,
                           @NotNull String htmlUrl,
                           @NotNull String diffUrl,
                           @NotNull String patchUrl,
                           @NotNull String issueUrl,
                           @NotNull Date createdAt,
                           @NotNull Date updatedAt,
                           @Nullable Date closedAt,
                           @Nullable Date mergedAt,
                           @NotNull GithubUser user,
                           @NotNull Link head,
                           @NotNull Link base) {
    myNumber = number;
    myState = state;
    myTitle = title;
    myBodyHtml = StringUtil.notNullize(bodyHtml);
    myHtmlUrl = htmlUrl;
    myDiffUrl = diffUrl;
    myPatchUrl = patchUrl;
    myIssueUrl = issueUrl;
    myCreatedAt = createdAt;
    myUpdatedAt = updatedAt;
    myClosedAt = closedAt;
    myMergedAt = mergedAt;
    myUser = user;
    myHead = head;
    myBase = base;
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
  public String getBodyHtml() {
    return myBodyHtml;
  }

  @NotNull
  public String getHtmlUrl() {
    return myHtmlUrl;
  }

  @NotNull
  public String getDiffUrl() {
    return myDiffUrl;
  }

  @NotNull
  public String getPatchUrl() {
    return myPatchUrl;
  }

  @NotNull
  public String getIssueUrl() {
    return myIssueUrl;
  }

  @NotNull
  public Date getCreatedAt() {
    return myCreatedAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return myUpdatedAt;
  }

  @Nullable
  public Date getClosedAt() {
    return myClosedAt;
  }

  @Nullable
  public Date getMergedAt() {
    return myMergedAt;
  }

  @NotNull
  public GithubUser getUser() {
    return myUser;
  }

  @NotNull
  public Link getHead() {
    return myHead;
  }

  @NotNull
  public Link getBase() {
    return myBase;
  }
}
