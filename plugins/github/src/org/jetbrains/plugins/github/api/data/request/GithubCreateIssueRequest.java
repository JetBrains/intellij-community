// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubCreateIssueRequest {
  @NotNull private final String title;
  @Nullable private final String body;
  @Nullable private final Long milestone;
  @Nullable private final List<String> labels;
  @Nullable private final List<String> assignees;

  public GithubCreateIssueRequest(@NotNull String title,
                                  @Nullable String body,
                                  @Nullable Long milestone,
                                  @Nullable List<String> labels,
                                  @Nullable List<String> assignees) {
    this.title = title;
    this.body = body;
    this.milestone = milestone;
    this.labels = labels;
    this.assignees = assignees;
  }
}
