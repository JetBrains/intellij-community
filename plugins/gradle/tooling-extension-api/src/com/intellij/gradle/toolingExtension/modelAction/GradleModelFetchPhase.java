// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.IntermediateResultHandler;
import org.jetbrains.annotations.NotNull;

public enum GradleModelFetchPhase {

  /**
   * Model providers, in this phase, fetch Gradle tooling models after gradle projects are loaded and before "sync" tasks are run.
   * This can be used to set up "sync" tasks for the import
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded(BuildAction, IntermediateResultHandler)
   */
  PROJECT_LOADED_PHASE("Project loaded phase"),

  /**
   * Model providers, in this phase, fetch an initial set of Gradle models for IDEA project structure.
   * These models form a minimal set to start working with a project: basic code insight of source code.
   * It is module names, source sets and content roots, project and module SDKs, language level, etc.
   * <p>
   * These model providers will be executed after "sync" tasks are run
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished(BuildAction, IntermediateResultHandler)
   */
  PROJECT_MODEL_PHASE("Project model phase"),

  /**
   * Model provides, in this phase, fetches rest of Gradle models, which needed for rich experience in IntelliJ IDEA.
   * It is a code insight in Gradle scripts, data for run configuration creation and for code completion in him, data for code profiling, etc.
   * <p>
   * These model providers will be executed after "sync" tasks are run
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished(BuildAction, IntermediateResultHandler)
   */
  ADDITIONAL_MODEL_PHASE("Additional model phase");

  private final @NotNull String displayName;

  GradleModelFetchPhase(@NotNull String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}