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
  private long number;
  @NotNull private String state;
  @NotNull private String title;
  @NotNull private String body;

  @NotNull private String url;
  @NotNull private String htmlUrl;
  @NotNull private String diffUrl;
  @NotNull private String patchUrl;
  @NotNull private String issueUrl;

  @NotNull private Date createdAt;
  @NotNull private Date updatedAt;
  @Nullable private Date closedAt;
  @Nullable private Date mergedAt;

  @NotNull private GithubUser user;

  @NotNull private Link head;
  @NotNull private Link base;

  public static class Link {
    @NotNull private String label;
    @NotNull private String ref;
    @NotNull private String sha;

    @NotNull private GithubRepo repo;
    @NotNull private GithubUser user;

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
      this.label = label;
      this.ref = ref;
      this.sha = sha;
      this.repo = repo;
      this.user = user;
    }

    @NotNull
    public String getLabel() {
      return label;
    }

    @NotNull
    public String getRef() {
      return ref;
    }

    @NotNull
    public String getSha() {
      return sha;
    }

    @NotNull
    public GithubRepo getRepo() {
      return repo;
    }

    @NotNull
    public GithubUser getUser() {
      return user;
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
    this.number = number;
    this.state = state;
    this.title = title;
    this.body = body;
    this.url = url;
    this.htmlUrl = htmlUrl;
    this.diffUrl = diffUrl;
    this.patchUrl = patchUrl;
    this.issueUrl = issueUrl;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.closedAt = closedAt;
    this.mergedAt = mergedAt;
    this.user = user;
    this.head = head;
    this.base = base;
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
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getHtmlUrl() {
    return htmlUrl;
  }

  @NotNull
  public String getDiffUrl() {
    return diffUrl;
  }

  @NotNull
  public String getPatchUrl() {
    return patchUrl;
  }

  @NotNull
  public String getIssueUrl() {
    return issueUrl;
  }

  @NotNull
  public Date getCreatedAt() {
    return createdAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return updatedAt;
  }

  @Nullable
  public Date getClosedAt() {
    return closedAt;
  }

  @Nullable
  public Date getMergedAt() {
    return mergedAt;
  }

  @NotNull
  public GithubUser getUser() {
    return user;
  }

  @NotNull
  public Link getHead() {
    return head;
  }

  @NotNull
  public Link getBase() {
    return base;
  }
}
