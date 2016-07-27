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

import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

@RestModel
@SuppressWarnings("UnusedDeclaration")
class GithubCommitRaw extends GithubCommitShaRaw {
  private GithubUserRaw author;
  private GithubUserRaw committer;

  @Mandatory private GitCommitRaw commit;

  @Mandatory private List<GithubCommitShaRaw> parents;

  public static class GitCommitRaw {
    private String url;
    @Mandatory private String message;

    @Mandatory private GitUserRaw author;
    @Mandatory private GitUserRaw committer;

    @NotNull
    public String getMessage() {
      return message;
    }

    @NotNull
    public GitUserRaw getAuthor() {
      return author;
    }

    @NotNull
    public GitUserRaw getCommitter() {
      return committer;
    }
  }

  public static class GitUserRaw {
    @Mandatory private String name;
    @Mandatory private String email;
    @Mandatory private Date date;

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

  @Nullable
  public GithubUserRaw getAuthor() {
    return author;
  }

  @Nullable
  public GithubUserRaw getCommitter() {
    return committer;
  }

  @NotNull
  public List<GithubCommitShaRaw> getParents() {
    return parents;
  }

  @NotNull
  public GitCommitRaw getCommit() {
    return commit;
  }
}
