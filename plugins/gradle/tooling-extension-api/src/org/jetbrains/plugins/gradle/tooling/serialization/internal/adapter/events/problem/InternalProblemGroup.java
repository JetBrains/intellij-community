// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.ProblemGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InternalProblemGroup implements ProblemGroup {

  private final @NotNull String name;
  private final @NotNull String displayName;
  private final @Nullable InternalProblemGroup parent;

  public InternalProblemGroup(
    @NotNull String name,
    @NotNull String displayName,
    @Nullable InternalProblemGroup parent
  ) {
    this.name = name;
    this.displayName = displayName;
    this.parent = parent;
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
  public @Nullable InternalProblemGroup getParent() {
    return parent;
  }
}
