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

import java.util.Date;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
class GithubCommitRaw {
  private String url;
  private String sha;

  private GithubUserRaw author;
  private GithubUserRaw committer;

  private GitCommitRaw commit;

  private CommitStatsRaw stats;
  private List<GithubFileRaw> files;

  private List<GithubCommitRaw> parents;

  public static class GitCommitRaw {
    private String url;
    private String message;

    private GitUserRaw author;
    private GitUserRaw committer;
  }

  public static class GitUserRaw {
    private String name;
    private String email;
    private Date date;
  }

  public static class CommitStatsRaw {
    private Integer additions;
    private Integer deletions;
    private Integer total;
  }
}
