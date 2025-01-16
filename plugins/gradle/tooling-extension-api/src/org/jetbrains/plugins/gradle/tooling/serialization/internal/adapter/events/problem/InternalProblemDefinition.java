// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.problem;

import org.gradle.tooling.events.problems.ProblemDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InternalProblemDefinition implements ProblemDefinition {

  private final @NotNull InternalProblemId id;
  private final @NotNull InternalSeverity severity;
  private final @Nullable InternalDocumentationLink documentationLink;

  public InternalProblemDefinition(
    @NotNull InternalProblemId id,
    @NotNull InternalSeverity severity,
    @Nullable InternalDocumentationLink documentationLink
  ) {
    this.id = id;
    this.severity = severity;
    this.documentationLink = documentationLink;
  }

  @Override
  public @NotNull InternalProblemId getId() {
    return id;
  }

  @Override
  public @NotNull InternalSeverity getSeverity() {
    return severity;
  }

  @Override
  public @Nullable InternalDocumentationLink getDocumentationLink() {
    return documentationLink;
  }
}
