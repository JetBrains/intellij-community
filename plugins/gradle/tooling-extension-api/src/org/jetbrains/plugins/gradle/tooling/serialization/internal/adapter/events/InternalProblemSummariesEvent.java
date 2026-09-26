// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.problems.ProblemSummariesEvent;
import org.gradle.tooling.events.problems.ProblemSummary;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class InternalProblemSummariesEvent extends InternalProgressEvent implements ProblemSummariesEvent {

  private final @NotNull List<ProblemSummary> problemSummaries;

  public InternalProblemSummariesEvent(
    @NotNull Long eventTime,
    @NotNull String displayName,
    @Nullable OperationDescriptor descriptor,
    @NotNull List<ProblemSummary> problemSummaries
  ) {
    super(eventTime, displayName, descriptor);
    this.problemSummaries = problemSummaries;
  }

  public InternalProblemSummariesEvent(
    @NotNull ProblemSummariesEvent event,
    @Nullable OperationDescriptor descriptor
  ) {
    super(event.getEventTime(), event.getDisplayName(), descriptor);
    this.problemSummaries = event.getProblemSummaries();
  }

  @Override
  public @NotNull List<ProblemSummary> getProblemSummaries() {
    return problemSummaries;
  }
}
