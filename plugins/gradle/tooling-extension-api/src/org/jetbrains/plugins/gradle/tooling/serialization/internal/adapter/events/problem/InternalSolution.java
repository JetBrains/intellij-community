// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.Solution;
import org.jetbrains.annotations.NotNull;

public class InternalSolution implements Solution {

  private final @NotNull String solution;

  public InternalSolution(@NotNull String solution) {
    this.solution = solution;
  }

  @Override
  public @NotNull String getSolution() {
    return solution;
  }
}
