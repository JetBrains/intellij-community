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

import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
class GithubCommitRaw {
  @Nullable public String url;
  @Nullable public String sha;

  @Nullable public GithubUserRaw author;
  @Nullable public GithubUserRaw committer;

  @Nullable public GitCommitRaw commit;

  @Nullable public CommitStatsRaw stats;
  @Nullable public List<GithubFileRaw> files;

  @Nullable public List<GithubCommitRaw> parents;

  public static class GitCommitRaw {
    @Nullable public String url;
    @Nullable public String message;

    @Nullable public GitUserRaw author;
    @Nullable public GitUserRaw committer;
  }

  public static class GitUserRaw {
    @Nullable public String name;
    @Nullable public String email;
    @Nullable public Date date;
  }

  public static class CommitStatsRaw {
    @Nullable public Integer additions;
    @Nullable public Integer deletions;
    @Nullable public Integer total;
  }
}
