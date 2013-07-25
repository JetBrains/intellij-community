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

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
class GithubRepoRaw implements DataConstructor<GithubRepo>, DataConstructorDetailed<GithubRepoDetailed> {
  @Nullable public Long id;
  @Nullable public String name;
  @Nullable public String fullName;
  @Nullable public String description;

  @SerializedName("private")
  @Nullable public Boolean isPrivate;
  @SerializedName("fork")
  @Nullable public Boolean isFork;

  @Nullable public String url;
  @Nullable public String htmlUrl;
  @Nullable public String cloneUrl;
  @Nullable public String gitUrl;
  @Nullable public String sshUrl;
  @Nullable public String svnUrl;
  @Nullable public String mirrorUrl;

  @Nullable public String homepage;
  @Nullable public String language;
  @Nullable public Integer size;

  @Nullable public Integer forks;
  @Nullable public Integer forksCount;
  @Nullable public Integer watchers;
  @Nullable public Integer watchersCount;
  @Nullable public Integer openIssues;
  @Nullable public Integer openIssuesCount;

  @Nullable public String masterBranch;
  @Nullable public String defaultBranch;

  @Nullable public Boolean hasIssues;
  @Nullable public Boolean hasWiki;
  @Nullable public Boolean hasDownloads;

  @Nullable public GithubRepoRaw parent;
  @Nullable public GithubRepoRaw source;

  @Nullable public GithubUserRaw owner;
  @Nullable public GithubUserRaw organization;

  @Nullable public Date pushedAt;
  @Nullable public Date createdAt;
  @Nullable public Date updatedAt;

  @NotNull
  @Override
  public GithubRepo create() {
    return new GithubRepo(name, fullName, description, isPrivate, isFork, htmlUrl, cloneUrl, defaultBranch, owner.create());
  }

  @NotNull
  @Override
  public GithubRepoDetailed createDetailed() {
    GithubRepo parent = this.parent == null ? null : this.parent.create();
    return new GithubRepoDetailed(name, fullName, description, isPrivate, isFork, htmlUrl, cloneUrl, defaultBranch, owner.create(), parent);
  }
}
