// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.ProblemId;
import org.gradle.tooling.events.problems.ProblemSummary;
import org.jetbrains.annotations.NotNull;

public class InternalProblemSummary implements ProblemSummary {

  private final @NotNull ProblemId id;
  private final @NotNull Integer count;

  public InternalProblemSummary(@NotNull ProblemId id, @NotNull Integer count) {
    this.id = id;
    this.count = count;
  }

  @Override
  public @NotNull ProblemId getProblemId() {
    return id;
  }

  @Override
  public @NotNull Integer getCount() {
    return count;
  }
}
