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

import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubCommitDetailed extends GithubCommit {
  @NotNull private final CommitStats myStats;
  @NotNull private final List<GithubFile> myFiles;

  public static class CommitStats {
    private final int myAdditions;
    private final int myDeletions;
    private final int myTotal;

    public CommitStats(int additions, int deletions, int total) {
      myAdditions = additions;
      myDeletions = deletions;
      myTotal = total;
    }

    public int getAdditions() {
      return myAdditions;
    }

    public int getDeletions() {
      return myDeletions;
    }

    public int getTotal() {
      return myTotal;
    }
  }

  public GithubCommitDetailed(@NotNull String url,
                              @NotNull String sha,
                              @Nullable GithubUser author,
                              @Nullable GithubUser committer,
                              @NotNull List<GithubCommitSha> parents,
                              @NotNull GitCommit commit,
                              @NotNull CommitStats stats,
                              @NotNull List<GithubFile> files) {
    super(url, sha, author, committer, parents, commit);
    myStats = stats;
    myFiles = files;
  }

  @NotNull
  public CommitStats getStats() {
    return myStats;
  }

  @NotNull
  public List<GithubFile> getFiles() {
    return myFiles;
  }
}
