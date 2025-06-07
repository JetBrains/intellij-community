// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnusedDeclaration")
public class GithubRepoDetailed extends GithubRepoWithPermissions {
  private Boolean allowSquashMerge;
  private Boolean allowMergeCommit;
  private Boolean allowRebaseMerge;
  private GithubOrg organization;
  private GithubRepo parent;
  private GithubRepo source;
  private Integer networkCount;
  private Integer subscribersCount;

  public boolean getAllowSquashMerge() {
    return allowSquashMerge != null ? allowSquashMerge : false;
  }

  public boolean getAllowMergeCommit() {
    return allowMergeCommit != null ? allowMergeCommit : false;
  }

  public boolean getAllowRebaseMerge() {
    return allowRebaseMerge != null ? allowRebaseMerge : false;
  }

  public @Nullable GithubRepo getParent() {
    return parent;
  }

  public @Nullable GithubRepo getSource() {
    return source;
  }
}
