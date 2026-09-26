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
import org.gradle.tooling.Failure
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import java.util.function.Function
import org.gradle.tooling.model.Model as GradleModel

/**
 * Gradle reports a missing model through different `UnknownModelException` classes depending on the code path:
 * the public [org.gradle.tooling.UnknownModelException] on the client side, and the internal
 * [org.gradle.tooling.provider.model.UnknownModelException] as the root cause on the provider side.
 */
private val UNKNOWN_MODEL_EXCEPTION_CLASS_NAMES = listOf(
  org.gradle.tooling.UnknownModelException::class.java.name,
  org.gradle.tooling.provider.model.UnknownModelException::class.java.name,
)

@Internal
class GradleModelControllerImpl(
  private val buildController: BuildController,
) : GradleModelController {

  /**
   * Fetches a required global Gradle model.
   *
   * @param modelClass the model type requested from the Tooling API.
   *
   * This method follows the Gradle Tooling API failure semantics when the requested model is not available.
   */
  fun <Model : Any> fetchRequiredModel(
    modelClass: Class<Model>,
    isValidModel: (Model) -> Boolean = { true },
  ): Model {
    if (!isResilientModelFetchApiUsed()) {
      return buildController.getModel(modelClass)
    }

    val result = buildController.fetch(modelClass)
    val model = result.model

    if (model == null || !isValidModel(model)) {
      // The resilient API can return a partial required model together with failures.
      // Fall back to strict getModel() to surface the original Gradle failure instead of
      // continuing with an invalid root model and failing later with an unrelated NPE.
      buildController.getModel(modelClass)

      // getModel() is expected to throw. Reaching this point means Gradle returned an unusable
      // required model without reporting the original failure.
      error("Strict Gradle model fetch unexpectedly returned invalid model for ${modelClass.name}")
    }

    sendModelFetchFailures(
      target = null,
      result = result,
      suppressFailures = false,
      optionalModel = false
    )
    return model
  }

  override fun <M : Any> fetchModelOrNull(
    modelClass: Class<M>,
  ): M? = fetchModel(
    target = null,
    modelClass = modelClass,
    modelParameter = null,
    suppressFailures = false,
    optionalModel = true
  )

  override fun <Target : GradleModel, Model : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
  ): Model? = fetchModel(
    target = target,
    modelClass = modelClass,
    modelParameter = null,
    suppressFailures = false,
    optionalModel = true
  )

  override fun <Target : GradleModel, Model : Any, Parameter : Any> fetchModelOrNull(
    target: Target,
    modelClass: Class<Model>,
    modelParameterClass: Class<Parameter>,
    modelParameterInitializer: Action<in Parameter>,
  ): Model? = fetchModel(
    target = target,
    modelClass = modelClass,
    modelParameter = GradleModelParameter(modelParameterClass, modelParameterInitializer),
    suppressFailures = false,
    optionalModel = true
  )

  override fun <Model : Any> fetchRequest(buildModels: Collection<GradleBuild>, modelClass: Class<Model>): GradleModelFetchRequest<Model> =
    GradleModelFetchRequestImpl(this, buildModels, modelClass)

  private fun <Model : Any> fetchModels(request: GradleModelFetchRequestImpl<Model>, modelConsumer: GradleModelConsumer) {

    fun <Target : GradleModel> fetchTargetModelsInParallel(targets: Collection<Target>, consumer: (Target, Model, Class<Model>) -> Unit) {
      val buildActions = targets.map { target ->
        BuildAction { innerBuildController ->
          val innerController = GradleModelControllerImpl(innerBuildController)
          val model = innerController.fetchModel(target, request.modelClass, request.modelParameter, request.suppressFailures, request.optionalModel)
          target to model
        }
      }
      val models = buildController.run(buildActions)
      for ((target, model) in models) {
        consumer(target, model ?: continue, request.modelClass)
      }
    }

    fun <Target : GradleModel> fetchTargetModelsInSequence(targets: Collection<Target>, consumer: (Target, Model, Class<Model>) -> Unit) {
      for (target in targets) {
        val model = fetchModel(target, request.modelClass, request.modelParameter, request.suppressFailures, request.optionalModel)
        consumer(target, model ?: continue, request.modelClass)
      }
    }

    fun <Target : GradleModel> fetchModels(targets: Collection<Target>, consumer: (Target, Model, Class<Model>) -> Unit) {
      when (request.executionMode) {
        GradleExecutionMode.DEFAULT -> when (isParallelModelFetchEnabled()) {
          true -> fetchTargetModelsInParallel(targets, consumer)
          else -> fetchTargetModelsInSequence(targets, consumer)
        }
        GradleExecutionMode.PARALLEL -> fetchTargetModelsInParallel(targets, consumer)
        GradleExecutionMode.SEQUENTIAL -> fetchTargetModelsInSequence(targets, consumer)
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

  private fun <Model : Any> fetchModel(
    target: GradleModel?,
    modelClass: Class<Model>,
    modelParameter: GradleModelParameter<*>?,
    suppressFailures: Boolean,
    optionalModel: Boolean,
  ): Model? {
    return when (isResilientModelFetchApiUsed()) {
      true -> {
        val fetchModelResult = when (modelParameter) {
          null -> when (target) {
            null -> buildController.fetch(modelClass)
            else -> buildController.fetch(target, modelClass)
          }
          else -> when (target) {
            null -> buildController.fetch(modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
            else -> buildController.fetch(target, modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
          }
        }
        fetchModelResult
          .also { sendModelFetchFailures(target, it, suppressFailures, optionalModel) }
          .getModel()
      }
      else -> handleModelFetchFailures(suppressFailures) {
        when (optionalModel) {
          true -> when (modelParameter) {
            null -> when (target) {
              null -> buildController.findModel(modelClass)
              else -> buildController.findModel(target, modelClass)
            }
            else -> when (target) {
              null -> buildController.findModel(modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
              else -> buildController.findModel(target, modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
            }
          }
          else -> when (modelParameter) {
            null -> when (target) {
              null -> buildController.getModel(modelClass)
              else -> buildController.getModel(target, modelClass)
            }
            else -> when (target) {
              null -> buildController.getModel(modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
              else -> buildController.getModel(target, modelClass, modelParameter.parameterClass, modelParameter.parameterInitializer)
            }
          }
        }
      }
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

  private fun <Model : Any> handleModelFetchFailures(suppressFailures: Boolean, action: () -> Model?): Model? {
    return runCatching(action).getOrElse { if (!suppressFailures) throw it else null }
  }

  private fun sendModelFetchFailures(
    target: GradleModel?,
    result: FetchModelResult<*>,
    suppressFailures: Boolean,
    optionalModel: Boolean,
  ) {
    if (suppressFailures) {
      return
    }
    val failures = result.failures.filterNot { optionalModel && it.isUnknownModelFailure() }
    if (failures.isEmpty()) {
      return
    }
    val targetPath = when (target) {
      is BasicGradleProject -> target.projectDirectory
      is GradleBuild -> target.buildIdentifier.rootDir
      else -> null
    }
    val failureResult = GradleModelFetchFailureResult(targetPath, failures.map { GradleModelFetchFailure(it) })
    buildController.send(GradleModelFetchFailureState(failureResult))
  }

  private fun Failure.isUnknownModelFailure(): Boolean {
    val description = description
    if (description != null && UNKNOWN_MODEL_EXCEPTION_CLASS_NAMES.any { description.startsWith(it) }) {
      return true
    }
    return causes.any { it.isUnknownModelFailure() }
  }

  private data class GradleModelFetchRequestImpl<Model : Any>(
    private val modelController: GradleModelControllerImpl,
    val buildModels: Collection<GradleBuild>,
    val modelClass: Class<Model>,
    val modelParameter: GradleModelParameter<*>? = null,
    val executionMode: GradleExecutionMode = GradleExecutionMode.DEFAULT,
    val targetLevel: GradleModelLevel = GradleModelLevel.PROJECT,
    val projectTraversal: GradleTraversalMode = GradleTraversalMode.DIRECT,
    val suppressFailures: Boolean = false,
    val optionalModel: Boolean = false,
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

    override fun suppressFailures(suppressFailures: Boolean): GradleModelFetchRequest<Model> =
      copy(suppressFailures = suppressFailures)

    override fun optionalModel(optionalModel: Boolean): GradleModelFetchRequest<Model> =
      copy(optionalModel = optionalModel)

    override fun execute(modelConsumer: GradleModelConsumer): Unit =
      modelController.fetchModels(this, modelConsumer)
  }

  private data class GradleModelParameter<Parameter : Any>(
    val parameterClass: Class<Parameter>,
    val parameterInitializer: Action<in Parameter>,
  )
}
