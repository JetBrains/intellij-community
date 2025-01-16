// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.Problem;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.util.List;

@ApiStatus.Internal
public final class InternalFailure implements Failure, Serializable {

  private final String message;
  private final String description;
  private final List<InternalFailure> causes;
  private final List<Problem> problems;

  public InternalFailure(
    String message,
    String description,
    List<InternalFailure> causes,
    List<Problem> problems
  ) {
    this.message = message;
    this.description = description;
    this.causes = causes;
    this.problems = problems;
  }

  @Override
  public String getMessage() {
    return this.message;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public List<? extends Failure> getCauses() {
    return this.causes;
  }

  @Override
  public List<Problem> getProblems() {
    return problems;
  }
}
