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
   * Model provider, in this phase, fetches and caches a Gradle task model into
   * {@link com.intellij.gradle.toolingExtension.impl.model.taskModel.GradleTaskCache}.
   * This cache is available by {@link org.jetbrains.plugins.gradle.tooling.ModelBuilderContext}.
   * <p>
   * These model providers will be executed after "sync" tasks are run
   * <p>
   * [TASK_WARM_UP_PHASE] should be first, because this phase evaluates all lazy Task configurations.
   * These configurations may modify a Gradle project model which is necessary for the following phases.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished(BuildAction, IntermediateResultHandler)
   */
  TASK_WARM_UP_PHASE("Task warm-up phase"),

  /**
   * Model providers, in this phase, configures dependency download policies for the dependency resolution in the next phase.
   * <p>
   * These model providers will be executed after "sync" tasks are run
   * <p>
   * [DEPENDENCY_DOWNLOAD_POLICY_PHASE] should be before [PROJECT_SOURCE_SET_PHASE],
   * because model builders in [PROJECT_SOURCE_SET_PHASE] resolves and downloads project dependencies.
   * In the future, the dependency resolution phase will be extracted from the source-set phase
   * and [DEPENDENCY_DOWNLOAD_POLICY_PHASE] will be a first part of the new phase.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished(BuildAction, IntermediateResultHandler)
   */
  DEPENDENCY_DOWNLOAD_POLICY_PHASE("Dependency download policy phase"),

  /**
   * Model providers, in this phase, fetch a Gradle project source set models and resolve dependencies.
   * <p>
   * These model providers will be executed after "sync" tasks are run
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished(BuildAction, IntermediateResultHandler)
   */
  PROJECT_SOURCE_SET_PHASE("Project source-set phase"),

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
   * It is a code insight in Gradle scripts, data for run configuration creation and for code completion in him,
   * data for code profiling, etc.
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