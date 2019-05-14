// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class GithubCommit extends GithubCommitSha {
  private GithubUser author;
  private GithubUser committer;

  private GitCommit commit;

  private List<GithubCommitSha> parents;

  @Nullable
  public GithubUser getAuthor() {
    return author;
  }

  @Nullable
  public GithubUser getCommitter() {
    return committer;
  }

  @NotNull
  public List<GithubCommitSha> getParents() {
    return parents;
  }

  @NotNull
  public GitCommit getCommit() {
    return commit;
  }

  public static class GitCommit {
    private String url;
    private String message;

    private GitUser author;
    private GitUser committer;

    @NotNull
    public String getMessage() {
      return message;
    }

    @NotNull
    public GitUser getAuthor() {
      return author;
    }

    @NotNull
    public GitUser getCommitter() {
      return committer;
    }
  }

  public static class GitUser {
    private String name;
    private String email;
    private Date date;

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
}
