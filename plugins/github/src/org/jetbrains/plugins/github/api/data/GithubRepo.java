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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;

//example/GithubRepo.json
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubRepo extends GithubRepoBasic {
  private Date createdAt;
  private Date updatedAt;
  private Date pushedAt;

  //more urls
  @Mandatory private String cloneUrl;
  //more urls

  private String homepage;

  private Integer size;

  private Integer stargazersCount;
  private Integer watchersCount;

  private String language;

  private Boolean hasIssues;
  private Boolean hasProjects;
  private Boolean hasWiki;
  private Boolean hasPages;
  private Boolean hasDownloads;

  private Integer forksCount;

  private String mirrorUrl;
  private Boolean archived;

  private Integer openIssuesCount;
  //private ??? license;
  private Integer forks;
  private Integer openIssues;
  private Integer watchers;
  private String defaultBranch;

  @Nullable
  public String getDefaultBranch() {
    return defaultBranch;
  }

  @NotNull
  public String getCloneUrl() {
    return cloneUrl;
  }
}
