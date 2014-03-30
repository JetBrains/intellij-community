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

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
class GithubPullRequestRaw implements DataConstructor {
  @Nullable public Long number;
  @Nullable public String state;
  @Nullable public String title;
  @Nullable public String body;
  @Nullable public String bodyHtml;

  @Nullable public String url;
  @Nullable public String htmlUrl;
  @Nullable public String diffUrl;
  @Nullable public String patchUrl;
  @Nullable public String issueUrl;

  @Nullable public Boolean merged;
  @Nullable public Boolean mergeable;

  @Nullable public Integer comments;
  @Nullable public Integer commits;
  @Nullable public Integer additions;
  @Nullable public Integer deletions;
  @Nullable public Integer changedFiles;

  @Nullable public Date createdAt;
  @Nullable public Date updatedAt;
  @Nullable public Date closedAt;
  @Nullable public Date mergedAt;

  @Nullable public GithubUserRaw user;

  @Nullable public LinkRaw head;
  @Nullable public LinkRaw base;

  public static class LinkRaw {
    @Nullable public String label;
    @Nullable public String ref;
    @Nullable public String sha;

    @Nullable public GithubRepoRaw repo;
    @Nullable public GithubUserRaw user;

    @NotNull
    public GithubPullRequest.Link create() {
      return new GithubPullRequest.Link(label, ref, sha, repo.createRepo(), user.createUser());
    }
  }

  @NotNull
  public GithubPullRequest createPullRequest() {
    return new GithubPullRequest(number, state, title, bodyHtml, htmlUrl, diffUrl, patchUrl, issueUrl, createdAt, updatedAt, closedAt, mergedAt,
                                 user.createUser(), head.create(), base.create());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T create(@NotNull Class<T> resultClass) {
    if (resultClass == GithubPullRequest.class) {
      return (T)createPullRequest();
    }

    throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
  }
}
