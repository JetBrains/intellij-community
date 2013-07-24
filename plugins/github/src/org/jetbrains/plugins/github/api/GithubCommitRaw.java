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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
class GithubCommitRaw
  implements DataConstructorSimplified<GithubCommitSha>, DataConstructor<GithubCommit>, DataConstructorDetailed<GithubCommitDetailed> {
  @Nullable public String url;
  @Nullable public String sha;

  @Nullable public GithubUserRaw author;
  @Nullable public GithubUserRaw committer;

  @Nullable public GitCommitRaw commit;

  @Nullable public CommitStatsRaw stats;
  @Nullable public List<GithubFileRaw> files;

  @Nullable public List<GithubCommitRaw> parents;

  public static class GitCommitRaw implements DataConstructor<GithubCommit.GitCommit> {
    @Nullable public String url;
    @Nullable public String message;

    @Nullable public GitUserRaw author;
    @Nullable public GitUserRaw committer;

    @NotNull
    @Override
    public GithubCommit.GitCommit create() {
      return new GithubCommit.GitCommit(message, author.create(), committer.create());
    }
  }

  public static class GitUserRaw implements DataConstructor<GithubCommit.GitUser> {
    @Nullable public String name;
    @Nullable public String email;
    @Nullable public Date date;

    @NotNull
    @Override
    public GithubCommit.GitUser create() {
      return new GithubCommit.GitUser(name, email, date);
    }
  }

  public static class CommitStatsRaw implements DataConstructor<GithubCommitDetailed.CommitStats> {
    @Nullable public Integer additions;
    @Nullable public Integer deletions;
    @Nullable public Integer total;

    @NotNull
    @Override
    public GithubCommitDetailed.CommitStats create() {
      return new GithubCommitDetailed.CommitStats(additions, deletions, total);
    }
  }

  @NotNull
  @Override
  public GithubCommitSha createSimplified() {
    return new GithubCommitSha(url, sha);
  }

  @NotNull
  @Override
  public GithubCommit create() {
    GithubUser author = this.author == null ? null : this.author.create();
    GithubUser committer = this.committer == null ? null : this.committer.create();

    List<GithubCommitSha> parents = new ArrayList<GithubCommitSha>();
    for (GithubCommitRaw raw : this.parents) {
      parents.add(raw.create());
    }
    return new GithubCommit(url, sha, author, committer, parents, commit.create());
  }

  @NotNull
  @Override
  public GithubCommitDetailed createDetailed() {
    GithubCommit commit = create();
    List<GithubFile> files = new ArrayList<GithubFile>();
    for (GithubFileRaw raw : this.files) {
      files.add(raw.create());
    }

    return new GithubCommitDetailed(commit.getUrl(), commit.getSha(), commit.getAuthor(), commit.getCommitter(), commit.getParents(),
                                    commit.getCommit(), stats.create(), files);
  }
}
