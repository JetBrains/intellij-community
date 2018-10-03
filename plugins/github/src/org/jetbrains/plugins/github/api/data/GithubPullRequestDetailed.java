// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequestDetailed extends GithubPullRequest {
  @Mandatory private Boolean merged;
  private Boolean mergeable;
  private Boolean rebaseable;
  private String mergeableState;
  private GithubUser mergedBy;

  private Integer comments;
  private Integer reviewComments;
  private Boolean maintainerCanModify;
  private Integer commits;
  private Integer additions;
  private Integer deletions;
  private Integer changedFiles;

  public boolean getMerged() {
    return merged;
  }

  public boolean getMergeable() {
    return mergeable != null && mergeable;
  }

  public boolean getRebaseable() {
    return rebaseable != null && rebaseable;
  }
}
