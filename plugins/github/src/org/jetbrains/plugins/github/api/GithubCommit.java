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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubCommit extends GithubCommitSha {
  @Nullable private GithubUser myAuthor;
  @Nullable private GithubUser myCommitter;

  @NotNull private List<GithubCommitSha> myParents;

  @NotNull private GitCommit myCommit;

  @NotNull
  public static GithubCommit create(@Nullable GithubCommitRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.parents == null) throw new JsonException("parents is null");
      GithubCommitSha sha = GithubCommitSha.createSha(raw);
      GithubUser author = raw.author == null ? null : GithubUser.create(raw.author);
      GithubUser committer = raw.committer == null ? null : GithubUser.create(raw.committer);
      GitCommit commit = GitCommit.create(raw.commit);

      List<GithubCommitSha> parents = new ArrayList<GithubCommitSha>();
      for (GithubCommitRaw rawParent : raw.parents) {
        parents.add(GithubCommitSha.createSha(rawParent));
      }

      return new GithubCommit(sha, author, committer, parents, commit);
    }
    catch (JsonException e) {
      throw new JsonException("GithubCommit parse error", e);
    }
  }

  public static class GitCommit implements Serializable {
    @NotNull private String myUrl;
    @NotNull private String myMessage;

    @NotNull private GitUser myAuthor;
    @NotNull private GitUser myCommitter;

    @NotNull
    public static GitCommit create(@Nullable GithubCommitRaw.GitCommitRaw raw) throws JsonException {
      try {
        if (raw == null) throw new JsonException("raw is null");
        if (raw.url == null) throw new JsonException("url is null");
        if (raw.message == null) throw new JsonException("message is null");

        GitUser author = GitUser.create(raw.author);
        GitUser committer = GitUser.create(raw.committer);

        return new GitCommit(raw.url, raw.message, author, committer);
      }
      catch (JsonException e) {
        throw new JsonException("GitCommit parse error", e);
      }
    }

    private GitCommit(@NotNull String url, @NotNull String message, @NotNull GitUser author, @NotNull GitUser committer) {
      this.myUrl = url;
      this.myMessage = message;
      this.myAuthor = author;
      this.myCommitter = committer;
    }

    @NotNull
    public String getUrl() {
      return myUrl;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }

    @NotNull
    public GitUser getAuthor() {
      return myAuthor;
    }

    @NotNull
    public GitUser getCommitter() {
      return myCommitter;
    }
  }

  public static class GitUser implements Serializable {
    @NotNull private String name;
    @NotNull private String email;
    @NotNull private Date date;

    @NotNull
    public static GitUser create(@Nullable GithubCommitRaw.GitUserRaw raw) throws JsonException {
      try {
        if (raw == null) throw new JsonException("raw is null");
        if (raw.name == null) throw new JsonException("name is null");
        if (raw.email == null) throw new JsonException("email is null");
        if (raw.date == null) throw new JsonException("date is null");

        return new GitUser(raw.name, raw.email, raw.date);
      }
      catch (JsonException e) {
        throw new JsonException("GitUser parse error", e);
      }
    }

    private GitUser(@NotNull String name, @NotNull String email, @NotNull Date date) {
      this.name = name;
      this.email = email;
      this.date = date;
    }

    @NotNull
    public String getName() {
      return name;
    }

    @NotNull
    public String getEmail() {
      return email;
    }

    @NotNull
    public Date getDate() {
      return date;
    }
  }

  protected GithubCommit(@NotNull GithubCommitSha sha,
                         @Nullable GithubUser author,
                         @Nullable GithubUser committer,
                         @NotNull List<GithubCommitSha> parents,
                         @NotNull GitCommit commit) {
    super(sha);
    this.myAuthor = author;
    this.myCommitter = committer;
    this.myParents = parents;
    this.myCommit = commit;
  }

  protected GithubCommit(GithubCommit commit) {
    super(commit.getUrl(), commit.getSha());
    this.myAuthor = commit.myAuthor;
    this.myCommitter = commit.myCommitter;
    this.myParents = commit.myParents;
    this.myCommit = commit.myCommit;
  }

  @Nullable
  public GithubUser getAuthor() {
    return myAuthor;
  }

  @Nullable
  public GithubUser getCommitter() {
    return myCommitter;
  }

  @NotNull
  public List<GithubCommitSha> getParents() {
    return myParents;
  }

  @NotNull
  public GitCommit getCommit() {
    return myCommit;
  }
}
