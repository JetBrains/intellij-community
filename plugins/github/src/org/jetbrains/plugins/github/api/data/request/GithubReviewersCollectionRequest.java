// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubReviewersCollectionRequest {
  @NotNull private final Collection<String> reviewers;
  @NotNull private final Collection<String> team_reviewers;

  public GithubReviewersCollectionRequest(@NotNull Collection<String> reviewers,
                                          @NotNull Collection<String> team_reviewers) {
    this.reviewers = reviewers;
    this.team_reviewers = team_reviewers;
  }
}
