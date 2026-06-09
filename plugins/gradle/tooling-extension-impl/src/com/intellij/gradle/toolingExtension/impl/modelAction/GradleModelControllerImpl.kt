// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import com.intellij.gradle.toolingExtension.GradleToolingExtensionProperties.isParallelModelFetchEnabled
import com.intellij.gradle.toolingExtension.GradleToolingExtensionProperties.isResilientModelFetchApiUsed
import com.intellij.gradle.toolingExtension.impl.util.GradleTreeTraverserUtil
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleModelLevel
import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleTraversalMode
import org.gradle.api.Action
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import java.util.function.Function
import org.gradle.tooling.model.Model as GradleModel

@Internal
class GradleModelControllerImpl(
  private val buildController: BuildController,
) : GradleModelController {

  override fun <Model : Any> fetchModel(modelClass: Class<Model>): Model {
    if (!isResilientModelFetchApiUsed()) {
      return buildController.getModel(modelClass)
    }
    return buildController.fetch(modelClass).getModel()
           ?: buildController.getModel(modelClass)
  }

  override fun <M : Any> fetchModelOrNull(modelClass: Class<M>): M? {
    if (!isResilientModelFetchApiUsed()) {
      return buildController.findModel(modelClass)
    }
    return buildController.fetch(modelClass)
      .alsoSendModelFetchFailures()
      .getModel()
  }

  private fun <Target : GradleModel, Model : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
    modelParameter: GradleModelParameter<*>?,
  ): Model? {
    if (modelParameter != null) {
      return fetchModelOrNull(target, modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
    }
    return fetchModelOrNull(target, modelClass)
  }

  override fun <Target : GradleModel, Model : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
  ): Model? {
    if (!isResilientModelFetchApiUsed()) {
      return buildController.findModel(target, modelClass)
    }
    return buildController.fetch(target, modelClass)
      .alsoSendModelFetchFailures()
      .getModel()
  }

  override fun <Target : GradleModel, Model : Any, Parameter : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
    modelParameterClass: Class<Parameter>,
    modelParameterInitializer: Action<in Parameter>,
  ): Model? {
    if (!isResilientModelFetchApiUsed()) {
      return buildController.findModel(target, modelClass, modelParameterClass, modelParameterInitializer)
    }
    return buildController.fetch(target, modelClass, modelParameterClass, modelParameterInitializer)
      .alsoSendModelFetchFailures()
      .getModel()
  }

  override fun <Model : Any> fetchRequest(buildModels: Collection<GradleBuild>, modelClass: Class<Model>): GradleModelFetchRequest<Model> =
    GradleModelFetchRequestImpl(this, buildModels, modelClass)

  private fun <Model : Any> fetchModels(request: GradleModelFetchRequestImpl<Model>, modelConsumer: GradleModelConsumer) {

    fun <Target : GradleModel> fetchModels(targets: Collection<Target>, consumer: (Target, Model, Class<Model>) -> Unit) {
      when (request.executionMode) {
        GradleExecutionMode.DEFAULT -> when (isParallelModelFetchEnabled()) {
          true -> fetchTargetModelsInParallel(targets, request.modelClass, request.modelParameter, consumer)
          else -> fetchTargetModelsInSequence(targets, request.modelClass, request.modelParameter, consumer)
        }
        GradleExecutionMode.PARALLEL -> fetchTargetModelsInParallel(targets, request.modelClass, request.modelParameter, consumer)
        GradleExecutionMode.SEQUENTIAL -> fetchTargetModelsInSequence(targets, request.modelClass, request.modelParameter, consumer)
      }
    }

    when (request.targetLevel) {
      GradleModelLevel.BUILD -> {
        fetchModels(request.buildModels, modelConsumer::consumeBuildModel)
      }
      GradleModelLevel.PROJECT -> {
        val projectModels = when (request.projectTraversal) {
          GradleTraversalMode.DIRECT -> collectAllProjectModels(request.buildModels)
          GradleTraversalMode.RECURSIVE -> collectAllProjectModelsRecursively(request.buildModels)
        }
        fetchModels(projectModels, modelConsumer::consumeProjectModel)
      }
    }
  }

  private fun <Target : GradleModel, Model : Any> fetchTargetModelsInParallel(
    targets: Collection<Target>,
    modelClass: Class<Model>,
    modelParameter: GradleModelParameter<*>?,
    modelConsumer: (Target, Model, Class<Model>) -> Unit,
  ) {
    val buildActions = targets.map { target ->
      BuildAction { innerBuildController ->
        val innerController = GradleModelControllerImpl(innerBuildController)
        val model = innerController.fetchModelOrNull(target, modelClass, modelParameter)
        target to (model ?: return@BuildAction null)
      }
    }
    val models = buildController.run(buildActions)
    for (model in models) {
      if (model != null) {
        modelConsumer(model.first, model.second, modelClass)
      }
    }
  }

  private fun <Target : GradleModel, Model : Any> fetchTargetModelsInSequence(
    targets: Collection<Target>,
    modelClass: Class<Model>,
    modelParameter: GradleModelParameter<*>?,
    modelConsumer: (Target, Model, Class<Model>) -> Unit,
  ) {
    for (target in targets) {
      val model = fetchModelOrNull(target, modelClass, modelParameter) ?: continue
      modelConsumer(target, model, modelClass)
    }
  }

  private fun collectAllProjectModels(buildModels: Collection<GradleBuild>): Collection<BasicGradleProject> {
    val projectModels = ArrayList<BasicGradleProject>()
    for (buildModel in buildModels) {
      projectModels.addAll(buildModel.projects)
    }
    return projectModels
  }

  private fun collectAllProjectModelsRecursively(buildModels: Collection<GradleBuild>): Collection<BasicGradleProject> {
    val projectModels = ArrayList<BasicGradleProject>()
    for (buildModel in buildModels) {
      for (rootProject in collectRootProjectModels(buildModel)) {
        GradleTreeTraverserUtil.breadthFirstTraverseTree(rootProject, Function { projectModel ->
          projectModels.add(projectModel)
          projectModel.children
        })
      }
    }
    return projectModels
  }

  private fun collectRootProjectModels(buildModel: GradleBuild): Collection<BasicGradleProject> {
    val rootProject = buildModel.rootProject
    if (rootProject != null) {
      return listOf(rootProject)
    }
    return buildModel.projects.filter { it.parent == null }
  }

  private fun <T : FetchModelResult<*>> T.alsoSendModelFetchFailures(): T = also { result ->
    val failures = result.failures.takeIf { it.isNotEmpty() } ?: return@also
    buildController.send(GradleModelFetchFailureState(failures.map { GradleModelFetchFailure(it) }))
  }

  private data class GradleModelFetchRequestImpl<Model : Any>(
    private val modelController: GradleModelControllerImpl,
    val buildModels: Collection<GradleBuild>,
    val modelClass: Class<Model>,
    val modelParameter: GradleModelParameter<*>? = null,
    val executionMode: GradleExecutionMode = GradleExecutionMode.DEFAULT,
    val targetLevel: GradleModelLevel = GradleModelLevel.PROJECT,
    val projectTraversal: GradleTraversalMode = GradleTraversalMode.DIRECT,
  ) : GradleModelFetchRequest<Model> {

    override fun modelLevel(targetLevel: GradleModelLevel): GradleModelFetchRequest<Model> =
      copy(targetLevel = targetLevel)

    override fun executionMode(executionMode: GradleExecutionMode): GradleModelFetchRequest<Model> =
      copy(executionMode = executionMode)

    override fun traversalMode(traversalMode: GradleTraversalMode): GradleModelFetchRequest<Model> =
      copy(projectTraversal = traversalMode)

    override fun <Parameter : Any> parameter(
      parameterClass: Class<Parameter>,
      parameterInitializer: Action<in Parameter>,
    ): GradleModelFetchRequest<Model> =
      copy(modelParameter = GradleModelParameter(parameterClass, parameterInitializer))

    override fun execute(modelConsumer: GradleModelConsumer): Unit =
      modelController.fetchModels(this, modelConsumer)
  }

  private data class GradleModelParameter<Parameter : Any>(
    val parameterClass: Class<Parameter>,
    val parameterInitializer: Action<in Parameter>,
  )
}
