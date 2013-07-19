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
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubCommitDetailed extends GithubCommit {
  @NotNull private CommitStats stats;
  @NotNull private List<GithubFile> files;

  public static class CommitStats implements Serializable {
    private int additions;
    private int deletions;
    private int total;

    @NotNull
    public static CommitStats create(@Nullable GithubCommitRaw.CommitStatsRaw raw) throws JsonException {
      try {
        if (raw == null) throw new JsonException("raw is null");
        if (raw.additions == null) throw new JsonException("additions is null");
        if (raw.deletions == null) throw new JsonException("deletions is null");
        if (raw.total == null) throw new JsonException("total is null");

        return new CommitStats(raw.additions, raw.deletions, raw.total);
      }
      catch (JsonException e) {
        throw new JsonException("CommitStats parse error", e);
      }
    }

    private CommitStats(int additions, int deletions, int total) {
      this.additions = additions;
      this.deletions = deletions;
      this.total = total;
    }

    public int getAdditions() {
      return additions;
    }

    public int getDeletions() {
      return deletions;
    }

    public int getTotal() {
      return total;
    }
  }

  @NotNull
  public static GithubCommitDetailed createDetailed(@Nullable GithubCommitRaw raw) throws JsonException {
    try {
      if (raw == null) throw new JsonException("raw is null");
      if (raw.stats == null) throw new JsonException("stats is null");
      if (raw.files == null) throw new JsonException("files is null");

      GithubCommit commit = GithubCommit.create(raw);
      CommitStats stats = CommitStats.create(raw.stats);
      List<GithubFile> files = new ArrayList<GithubFile>();
      for (GithubFileRaw rawFile : raw.files) {
        files.add(GithubFile.create(rawFile));
      }

      return new GithubCommitDetailed(commit, stats, files);
    }
    catch (JsonException e) {
      throw new JsonException("GithubCommitDetailed parse error", e);
    }
  }

  protected GithubCommitDetailed(@NotNull GithubCommit commit, @NotNull CommitStats stats, @NotNull List<GithubFile> files) {
    super(commit);
    this.stats = stats;
    this.files = files;
  }

  @NotNull
  public CommitStats getStats() {
    return stats;
  }

  @NotNull
  public List<GithubFile> getFiles() {
    return files;
  }
}
