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
public class GithubPullRequest {
  private long myNumber;
  @NotNull private String myState;
  @NotNull private String myTitle;
  @NotNull private String myBody;

  @NotNull private String myUrl;
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

    @NotNull
    public static Link create(@Nullable GithubPullRequestRaw.LinkRaw raw) throws JsonException {
      try {
        if (raw == null) throw new JsonException("raw is null");
        if (raw.label == null) throw new JsonException("label is null");
        if (raw.ref == null) throw new JsonException("ref is null");
        if (raw.sha == null) throw new JsonException("sha is null");

        GithubUser user = GithubUser.create(raw.user);
        GithubRepo repo = GithubRepo.create(raw.repo);

        return new Link(raw.label, raw.ref, raw.sha, repo, user);
      }
      catch (JsonException e) {
        throw new JsonException("Link parse error", e);
      }
    }

    private Link(@NotNull String label, @NotNull String ref, @NotNull String sha, @NotNull GithubRepo repo, @NotNull GithubUser user) {
      this.myLabel = label;
      this.myRef = ref;
      this.mySha = sha;
      this.myRepo = repo;
      this.myUser = user;
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

  public static GithubPullRequest create(GithubPullRequestRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.number == null) throw new JsonException("number is null");
      if (raw.state == null) throw new JsonException("state is null");
      if (raw.title == null) throw new JsonException("title is null");
      if (raw.body == null) throw new JsonException("body is null");

      if (raw.url == null) throw new JsonException("url is null");
      if (raw.htmlUrl == null) throw new JsonException("htmlUrl is null");
      if (raw.diffUrl == null) throw new JsonException("diffUrl is null");
      if (raw.patchUrl == null) throw new JsonException("patchUrl is null");
      if (raw.issueUrl == null) throw new JsonException("issueUrl is null");
      if (raw.createdAt == null) throw new JsonException("createdAt is null");
      if (raw.updatedAt == null) throw new JsonException("updatedAt is null");

      GithubUser user = GithubUser.create(raw.user);
      Link head = Link.create(raw.head);
      Link base = Link.create(raw.base);

      return new GithubPullRequest(raw.number, raw.state, raw.title, raw.body, raw.url, raw.htmlUrl, raw.diffUrl, raw.patchUrl,
                                   raw.issueUrl, raw.createdAt, raw.updatedAt, raw.closedAt, raw.mergedAt, user, head, base);
    }
    catch (JsonException e) {
      throw new JsonException("GithubPullRequest parse error", e);
    }
  }

  private GithubPullRequest(long number,
                            @NotNull String state,
                            @NotNull String title,
                            @NotNull String body,
                            @NotNull String url,
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
    this.myNumber = number;
    this.myState = state;
    this.myTitle = title;
    this.myBody = body;
    this.myUrl = url;
    this.myHtmlUrl = htmlUrl;
    this.myDiffUrl = diffUrl;
    this.myPatchUrl = patchUrl;
    this.myIssueUrl = issueUrl;
    this.myCreatedAt = createdAt;
    this.myUpdatedAt = updatedAt;
    this.myClosedAt = closedAt;
    this.myMergedAt = mergedAt;
    this.myUser = user;
    this.myHead = head;
    this.myBase = base;
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
  public String getUrl() {
    return myUrl;
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
