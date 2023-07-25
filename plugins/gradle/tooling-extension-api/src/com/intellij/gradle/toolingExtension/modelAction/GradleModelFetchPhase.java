// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import org.jetbrains.annotations.NotNull;

public class GradleModelFetchPhase implements Comparable<GradleModelFetchPhase> {

  /**
   * Model providers, in this phase, fetches initial set of Gradle models for IDEA project structure.
   * These models form minimal set for start working with project: basic code insight of source code.
   * It is module names, source sets and content roots, project and module SDKs, language level, etc.
   */
  public static final GradleModelFetchPhase PROJECT_MODEL_PHASE =
    new GradleModelFetchPhase(0, "Project model phase");

  /**
   * Model provides, in this phase, fetches rest of Gradle models, which needed for rich experience in Intellij IDEA.
   * It is code insight in Gradle scripts, data for run configuration creation and for code completion in him, data for code profiling, etc.
   */
  public static final GradleModelFetchPhase ADDITIONAL_MODEL_PHASE =
    new GradleModelFetchPhase(1000, "Additional model phase");

  private final int priority;
  private final @NotNull String displayName;

  public GradleModelFetchPhase(int priority, @NotNull String displayName) {
    this.priority = priority;
    this.displayName = displayName;
  }

  @Override
  public int compareTo(@NotNull GradleModelFetchPhase o) {
    return priority - o.priority;
  }

  @Override
  public String toString() {
    return displayName;
  }
}