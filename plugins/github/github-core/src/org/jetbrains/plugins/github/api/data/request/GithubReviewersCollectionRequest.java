// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubReviewersCollectionRequest {
  private final @NotNull Collection<String> reviewers;
  private final @NotNull Collection<String> teamReviewers;

  public GithubReviewersCollectionRequest(@NotNull Collection<String> reviewers,
                                          @NotNull Collection<String> teamReviewers) {
    this.reviewers = reviewers;
    this.teamReviewers = teamReviewers;
  }
}
