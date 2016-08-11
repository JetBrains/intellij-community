/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api.data;

import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubCommit extends GithubCommitSha {
  private GithubUser author;
  private GithubUser committer;

  @Mandatory private GitCommit commit;

  @Mandatory private List<GithubCommitSha> parents;

  @RestModel
  public static class GitCommit {
    private String url;
    @Mandatory private String message;

    @Mandatory private GitUser author;
    @Mandatory private GitUser committer;

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

  @RestModel
  public static class GitUser {
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
}
