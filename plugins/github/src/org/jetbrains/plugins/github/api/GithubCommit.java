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
  @SuppressWarnings("ConstantConditions")
  public static GithubCommit create(@Nullable GithubCommitRaw raw) throws JsonException {
    try {
      return new GithubCommit(raw);
    }
    catch (IllegalArgumentException e) {
      throw new JsonException("GithubCommit parse error", e);
    }
    catch (JsonException e) {
      throw new JsonException("GithubCommit parse error", e);
    }
  }

  public static class GitCommit implements Serializable {
    @NotNull private String myMessage;

    @NotNull private GitUser myAuthor;
    @NotNull private GitUser myCommitter;

    @SuppressWarnings("ConstantConditions")
    protected GitCommit(@NotNull GithubCommitRaw.GitCommitRaw raw) {
      myMessage = raw.message;
      myAuthor = new GitUser(raw.author);
      myCommitter = new GitUser(raw.committer);
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

    @SuppressWarnings("ConstantConditions")
    protected GitUser(@NotNull GithubCommitRaw.GitUserRaw raw) {
      name = raw.name;
      email = raw.email;
      date = raw.date;
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

  @SuppressWarnings("ConstantConditions")
  protected GithubCommit(@NotNull GithubCommitRaw raw) throws JsonException {
    super(raw);
    myAuthor = GithubUser.create(raw.author);
    myCommitter = GithubUser.create(raw.committer);
    myCommit = new GitCommit(raw.commit);

    if (raw.parents == null) throw new JsonException("parents is null");
    myParents = new ArrayList<GithubCommitSha>();
    for (GithubCommitRaw rawCommit : raw.parents) {
      myParents.add(GithubCommitSha.createSha(rawCommit));
    }
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
