// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.events.problems.Solution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFailure;

import java.util.List;

public class InternalProblem implements Problem {

  private final @NotNull InternalProblemDefinition definition;
  private final @NotNull InternalContextualLabel contextualLabel;
  private final @NotNull InternalDetails details;
  private final @NotNull List<Location> originLocations;
  private final @NotNull List<Location> contextualLocations;
  private final @NotNull List<Solution> solutions;
  private final @Nullable InternalFailure failure;
  private final @NotNull InternalAdditionalData additionalData;

  public InternalProblem(
    @NotNull InternalProblemDefinition definition,
    @NotNull InternalContextualLabel contextualLabel,
    @NotNull InternalDetails details,
    @NotNull List<Location> originLocations,
    @NotNull List<Location> contextualLocations,
    @NotNull List<Solution> solutions,
    @Nullable InternalFailure failure,
    @NotNull InternalAdditionalData additionalData
  ) {
    this.definition = definition;
    this.contextualLabel = contextualLabel;
    this.details = details;
    this.originLocations = originLocations;
    this.contextualLocations = contextualLocations;
    this.solutions = solutions;
    this.failure = failure;
    this.additionalData = additionalData;
  }

  @Override
  public @NotNull InternalProblemDefinition getDefinition() {
    return definition;
  }

  @Override
  public @NotNull InternalContextualLabel getContextualLabel() {
    return contextualLabel;
  }

  @Override
  public @NotNull InternalDetails getDetails() {
    return details;
  }

  @Override
  public @NotNull List<Location> getOriginLocations() {
    return originLocations;
  }

  @Override
  public @NotNull List<Location> getContextualLocations() {
    return contextualLocations;
  }

  @Override
  public @NotNull List<Solution> getSolutions() {
    return solutions;
  }

  @Override
  public @Nullable InternalFailure getFailure() {
    return failure;
  }

  @Override
  public @NotNull InternalAdditionalData getAdditionalData() {
    return additionalData;
  }
}
