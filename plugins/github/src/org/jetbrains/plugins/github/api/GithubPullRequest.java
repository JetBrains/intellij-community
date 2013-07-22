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

import static org.jetbrains.plugins.github.api.GithubPullRequestRaw.LinkRaw;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequest {
  private long myNumber;
  @NotNull private String myState;
  @NotNull private String myTitle;
  @NotNull private String myBody;

  @NotNull private String myHtmlUrl;
  @NotNull private String myDiffUrl;
  @NotNull private String myPatchUrl;
  @NotNull private String myIssueUrl;

  @NotNull private Date myCreatedAt;
  @NotNull private Date myUpdatedAt;
  @Nullable private Date myClosedAt;
  @Nullable private Date myMergedAt;

  @NotNull private GithubUser myUser;

  @NotNull private Link myHead;
  @NotNull private Link myBase;

  public static class Link {
    @NotNull private String myLabel;
    @NotNull private String myRef;
    @NotNull private String mySha;

    @NotNull private GithubRepo myRepo;
    @NotNull private GithubUser myUser;

    @SuppressWarnings("ConstantConditions")
    protected Link(@NotNull LinkRaw raw) throws JsonException {
      this(raw.label, raw.ref, raw.sha, raw.repo, raw.user);
    }

    private Link(@NotNull String label, @NotNull String ref, @NotNull String sha, @NotNull GithubRepoRaw repo, @NotNull GithubUserRaw user)
      throws JsonException {
      myLabel = label;
      myRef = ref;
      mySha = sha;
      myRepo = GithubRepo.create(repo);
      myUser = GithubUser.create(user);
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

  @SuppressWarnings("ConstantConditions")
  public static GithubPullRequest create(GithubPullRequestRaw raw) throws JsonException {
    try {
      return new GithubPullRequest(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubPullRequest parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubPullRequest parse error", e);
    }
  }

  @SuppressWarnings("ConstantConditions")
  protected GithubPullRequest(@NotNull GithubPullRequestRaw raw) throws JsonException {
    this(raw.number, raw.state, raw.title, raw.body, raw.htmlUrl, raw.diffUrl, raw.patchUrl, raw.issueUrl, raw.createdAt, raw.updatedAt,
         raw.closedAt, raw.mergedAt, raw.user, raw.head, raw.base);
  }

  private GithubPullRequest(long number,
                            @NotNull String state,
                            @NotNull String title,
                            @NotNull String body,
                            @NotNull String htmlUrl,
                            @NotNull String diffUrl,
                            @NotNull String patchUrl,
                            @NotNull String issueUrl,
                            @NotNull Date createdAt,
                            @NotNull Date updatedAt,
                            @Nullable Date closedAt,
                            @Nullable Date mergedAt,
                            @NotNull GithubUserRaw user,
                            @NotNull LinkRaw head,
                            @NotNull LinkRaw base) throws JsonException {
    myNumber = number;
    myState = state;
    myTitle = title;
    myBody = body;
    myHtmlUrl = htmlUrl;
    myDiffUrl = diffUrl;
    myPatchUrl = patchUrl;
    myIssueUrl = issueUrl;
    myCreatedAt = createdAt;
    myUpdatedAt = updatedAt;
    myClosedAt = closedAt;
    myMergedAt = mergedAt;
    myUser = GithubUser.create(user);
    myHead = new Link(head);
    myBase = new Link(base);
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
