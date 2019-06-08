// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@SuppressWarnings("UnusedDeclaration")
public class GithubRepo extends GithubRepoBasic {
  private Date createdAt;
  private Date updatedAt;
  private Date pushedAt;

  //more urls
  private String cloneUrl;
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
