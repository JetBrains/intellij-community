// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.ProblemId;
import org.jetbrains.annotations.NotNull;

public class InternalProblemId implements ProblemId {

  private final @NotNull String name;
  private final @NotNull String displayName;
  private final @NotNull InternalProblemGroup problemGroup;

  public InternalProblemId(
    @NotNull String name,
    @NotNull String displayName,
    @NotNull InternalProblemGroup problemGroup
  ) {
    this.name = name;
    this.displayName = displayName;
    this.problemGroup = problemGroup;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @NotNull String getDisplayName() {
    return displayName;
  }

  @Override
  public @NotNull InternalProblemGroup getGroup() {
    return problemGroup;
  }
}
