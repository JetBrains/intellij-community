// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.Problem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem.InternalProblem;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class InternalFailure implements Failure, Serializable {

  private final @Nullable String message;
  private final @Nullable String description;
  private final @Nullable List<? extends Failure> causes;
  private final @Nullable List<Problem> problems;

  public InternalFailure(
    @Nullable String message,
    @Nullable String description,
    @Nullable List<? extends Failure> causes,
    @Nullable List<Problem> problems
  ) {
    this.message = message;
    this.description = description;
    this.causes = causes;
    this.problems = problems;
  }

  public InternalFailure(@NotNull Failure failure) {
    this(
      failure.getMessage(),
      failure.getDescription(),
      failure.getCauses() == null ? null : toInternalFailures(failure.getCauses()),
      failure.getProblems() == null ? null : toInternalProblem(failure.getProblems())
    );
  }


  @Override
  public @Nullable String getMessage() {
    return this.message;
  }

  @Override
  public @Nullable String getDescription() {
    return this.description;
  }

  @Override
  public @Nullable List<? extends Failure> getCauses() {
    return this.causes;
  }

  @Override
  public @Nullable List<Problem> getProblems() {
    return problems;
  }

  private static @NotNull List<? extends Failure> toInternalFailures(@NotNull List<? extends Failure> failures) {
    //noinspection SSBasedInspection
    return failures.stream()
      .map(InternalFailure::new)
      .collect(Collectors.toList());
  }

  private static @NotNull List<Problem> toInternalProblem(@NotNull List<Problem> problems) {
    //noinspection SSBasedInspection
    return problems.stream()
      .map(InternalProblem::new)
      .collect(Collectors.toList());
  }
}
