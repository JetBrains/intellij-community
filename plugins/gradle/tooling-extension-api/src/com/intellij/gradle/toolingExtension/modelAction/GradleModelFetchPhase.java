// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction;

import org.jetbrains.annotations.NotNull;

public enum GradleModelFetchPhase {

  /**
   * Model providers, in this phase, fetch Gradle tooling models after gradle projects are loaded and before "sync" tasks are run.
   * This can be used to set up "sync" tasks for the import
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
   */
  PROJECT_LOADED_PHASE("Project loaded phase"),

  /**
   * Model providers, in this phase, warm up Gradle tasks configurations.
   * <p>
   * This phase should be first, because this phase:
   * 1. Warmed tasks don't throw configuration exceptions during {@link org.gradle.api.Project#getTasks};
   * 2. It evaluates all lazy task configurations that may modify a Gradle project model which is necessary for the following phases.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
   */
  WARM_UP_PHASE("Configuration warm-up phase"),

  /**
   * Model providers, in this phase, fetch a Gradle project identification models.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
   */
  PROJECT_MODEL_PHASE("Project model phase"),

  /**
   * Model providers, in this phase, fetch a Gradle project source set models and resolve dependencies.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
   */
  PROJECT_SOURCE_SET_PHASE("Project source set phase"),

  /**
   * Model providers, in this phase:
   * <ul>
   * <li>Configures dependency download policies;</li>
   * <li>Resolves dependencies for the project source sets.</li>
   * </ul>
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
   */
  PROJECT_SOURCE_SET_DEPENDENCY_PHASE("Project source set dependency phase"),

  /**
   * Model provides, in this phase, fetches rest of Gradle models, which needed for rich experience in IntelliJ IDEA.
   * It is a code insight in Gradle scripts, data for run configuration creation and for code completion in him,
   * data for code profiling, etc.
   *
   * @see org.gradle.tooling.BuildActionExecuter.Builder#buildFinished
   * @see org.gradle.tooling.BuildActionExecuter#setStreamedValueListener
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