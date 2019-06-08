// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public GithubRepo getParent() {
    return parent;
  }

  @Nullable
  public GithubRepo getSource() {
    return source;
  }
}
