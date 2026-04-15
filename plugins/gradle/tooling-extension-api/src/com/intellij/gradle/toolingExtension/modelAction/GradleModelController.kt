// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.modelAction

import org.gradle.api.Action
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import org.gradle.tooling.model.Model as GradleModel

/**
 * Fetches Gradle Tooling API models during project import.
 *
 * This controller hides the low-level Tooling API used by the import action. It can request build-level models, target-specific models,
 * and parameterized models, and then pass fetched models to a [GradleModelConsumer].
 */
@NonExtendable
interface GradleModelController {

  /**
   * Fetches a required global Gradle model.
   *
   * @param modelClass the model type requested from the Tooling API.
   *
   * This method follows the Gradle Tooling API failure semantics when the requested model is not available.
   */
  fun <M : Any> fetchModel(modelClass: Class<M>): M

  /**
   * Fetches an optional global Gradle model.
   *
   * @param modelClass the model type requested from the Tooling API.
   *
   * @return the fetched model, or `null` when the requested model is not available.
   */
  fun <M : Any> fetchModelOrNull(modelClass: Class<M>): M?

  /**
   * Fetches an optional model for the given Gradle Tooling API target.
   *
   * @param target Gradle target to fetch the model for.
   * Usually a [GradleBuild] for build-level models or a [BasicGradleProject] for project-level models.
   * @param modelClass the model type requested from the Tooling API.
   *
   * @return the fetched model, or `null` when the requested model is not available for [target].
   */
  fun <Target : GradleModel, Model : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
  ): Model?

  /**
   * Fetches an optional model for the given Gradle Tooling API target.
   *
   * @param target Gradle target to fetch the model for.
   * Usually a [GradleBuild] for build-level models or a [BasicGradleProject] for project-level models.
   * @param modelClass the model type requested from the Tooling API.
   *
   * @return the fetched model, or `null` when the requested model is not available for [target].
   */
  fun <Target : GradleModel, Model : Any, Parameter : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
    modelParameterClass: Class<Parameter>,
    modelParameterInitializer: Action<in Parameter>,
  ): Model?

  /**
   * Prepares a request for fetching models from Gradle build or project targets.
   *
   * The returned request can be configured with parameterized model settings, target level, project traversal, and execution mode.
   * Model fetching starts only when [GradleModelFetchRequest.execute] is called.
   *
   * @param buildModels Gradle builds participating in the import.
   * @param modelClass the model type requested from the Tooling API.
   */
  fun <Model : Any> fetchRequest(buildModels: Collection<GradleBuild>, modelClass: Class<Model>): GradleModelFetchRequest<Model>

  @NonExtendable
  interface GradleModelFetchRequest<Model : Any> {

    fun modelLevel(targetLevel: GradleModelLevel): GradleModelFetchRequest<Model>

    fun executionMode(executionMode: GradleExecutionMode): GradleModelFetchRequest<Model>

    fun traversalMode(traversalMode: GradleTraversalMode): GradleModelFetchRequest<Model>

    fun <Parameter : Any> parameter(
      parameterClass: Class<Parameter>,
      parameterInitializer: Action<in Parameter>,
    ): GradleModelFetchRequest<Model>

    fun execute(modelConsumer: GradleModelConsumer)

    enum class GradleExecutionMode {
      DEFAULT,
      PARALLEL,
      SEQUENTIAL,
    }

    enum class GradleModelLevel {
      BUILD,
      PROJECT,
    }

    enum class GradleTraversalMode {
      DIRECT,
      RECURSIVE,
    }
  }
}
