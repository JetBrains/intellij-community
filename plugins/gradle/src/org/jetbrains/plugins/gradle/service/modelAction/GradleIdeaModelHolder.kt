// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.modelAction

import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelHolderState
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelId
import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.PathMapper
import org.gradle.tooling.model.BuildModel
import org.gradle.tooling.model.ProjectModel
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.util.GradleObjectTraverser
import org.jetbrains.plugins.gradle.util.telemetry.GradleOpenTelemetryTraceService
import java.io.File

/**
 * @see com.intellij.gradle.toolingExtension.impl.modelAction.GradleDaemonModelHolder
 */
@ApiStatus.Internal
class GradleIdeaModelHolder(
  private val pathMapper: PathMapper? = null,
  private var buildEnvironment: BuildEnvironment? = null
) {

  private var rootBuild: GradleLightBuild? = null
  private val nestedBuilds = ArrayList<GradleLightBuild>()

  private val models: MutableMap<GradleModelId, Any> = LinkedHashMap()
  private val buildIdMapping: MutableMap<String, String> = LinkedHashMap()

  private val serializer = ToolingSerializer()
  private val modelPathConverter = GradleObjectTraverser(
    classesToSkip = setOf(String::class.java),
    classesToSkipChildren = setOf(Object::class.java, File::class.java)
  )

  fun getBuildEnvironment(): BuildEnvironment? {
    return buildEnvironment
  }

  fun getRootBuild(): GradleLightBuild {
    return rootBuild!!
  }

  fun getNestedBuilds(): List<GradleLightBuild> {
    return nestedBuilds
  }

  fun getAllBuilds(): List<GradleLightBuild> {
    val result = ArrayList<GradleLightBuild>()
    result.add(getRootBuild())
    result.addAll(getNestedBuilds())
    return result
  }

  fun hasModulesWithModel(modelClass: Class<*>): Boolean {
    return models.keys.any { it.isForClass(modelClass) }
  }

  fun <T> getRootModel(modelClazz: Class<T>): T? {
    return getBuildModel(getRootBuild(), modelClazz)
  }

  fun <T> getBuildModel(buildModel: BuildModel, modelClass: Class<T>): T? {
    val modelId = getBuildModelId(buildModel, modelClass)
    return getRootModel(modelId, modelClass)
  }

  fun <T> getProjectModel(projectModel: ProjectModel, modelClass: Class<T>): T? {
    val modelId = getProjectModelId(projectModel, modelClass)
    return getRootModel(modelId, modelClass)
  }

  private fun <T> getRootModel(modelId: GradleModelId, modelClass: Class<T>): T? {
    val model = models[modelId]
    if (model == null) {
      return null
    }
    if (modelClass.isInstance(model)) {
      @Suppress("UNCHECKED_CAST")
      return model as T
    }

    convertModelsWithType(modelClass)

    @Suppress("UNCHECKED_CAST")
    return models[modelId] as T?
  }

  private fun convertModelsWithType(modelClass: Class<*>) {
    val iterator = models.entries.iterator()
    while (iterator.hasNext()) {
      val (modelId, model) = iterator.next()
      if (!modelId.isForClass(modelClass)) {
        continue
      }
      val deserializedModel = deserializeModel(model, modelId, modelClass)
      if (deserializedModel == null) {
        iterator.remove()
        continue
      }
      convertModelPathsInPlace(deserializedModel)
      models[modelId] = deserializedModel
    }
  }

  private fun <T : Any> deserializeModel(model: Any, modelId: GradleModelId, modelClass: Class<T>): T? {
    if (model !is ByteArray) {
      return null
    }
    val deserializedModel = try {
      serializer.read(model, modelClass)
    }
    catch (e: Exception) {
      LOG.error("Failed to deserialize model with id [$modelId]", e)
      return null
    }
    if (deserializedModel == null || !modelClass.isInstance(deserializedModel)) {
      return null
    }
    return deserializedModel
  }

  fun <T : Any> addProjectModel(project: ProjectModel, modelClass: Class<T>, model: T) {
    val modelId = getProjectModelId(project, modelClass)
    models[modelId] = model
  }

  fun addState(state: GradleModelHolderState) {
    val rootBuild = state.rootBuild
    val nestedBuilds = state.nestedBuilds
    val buildEnvironment = state.buildEnvironment
    val telemetry = state.openTelemetry
    val models = state.models

    if (rootBuild != null) {
      convertBuildModelPathsInPlace(rootBuild)
      this.rootBuild = rootBuild
    }
    for (nestedBuild in nestedBuilds) {
      convertBuildModelPathsInPlace(nestedBuild)
      this.nestedBuilds.add(nestedBuild)
    }
    if (buildEnvironment != null) {
      convertModelPathsInPlace(buildEnvironment)
      this.buildEnvironment = buildEnvironment
    }
    this.models.putAll(models)

    GradleOpenTelemetryTraceService.exportOpenTelemetry(telemetry)
  }

  private fun convertModelPathsInPlace(model: Any) {
    if (pathMapper == null) return
    modelPathConverter.walk(model) { remoteFile ->
      if (remoteFile is File) {
        val remotePath = remoteFile.path
        if (pathMapper.canReplaceRemote(remotePath)) {
          val localPath = pathMapper.convertToLocal(remotePath)
          try {
            val field = File::class.java.getDeclaredField("path")
            field.isAccessible = true
            field.set(remoteFile, localPath)
          }
          catch (reflectionError: Throwable) {
            LOG.error("Failed to update mapped file", reflectionError)
          }
        }
      }
    }
  }

  private fun convertBuildModelPathsInPlace(build: GradleLightBuild) {
    val originalBuildId = GradleModelId.createBuildId(build.buildIdentifier)
    convertModelPathsInPlace(build)
    val currentBuildId = GradleModelId.createBuildId(build.buildIdentifier)
    if (originalBuildId != currentBuildId) {
      buildIdMapping[currentBuildId] = originalBuildId
    }
  }

  private fun applyBuildIdMapping(modelId: GradleModelId): GradleModelId {
    val originalBuildId = buildIdMapping[modelId.buildId] ?: return modelId
    return GradleModelId(modelId.classId, originalBuildId, modelId.projectId)
  }

  private fun getBuildModelId(buildModel: BuildModel, modelClass: Class<*>): GradleModelId {
    val modelId = GradleModelId.createBuildModelId(buildModel, modelClass)
    return applyBuildIdMapping(modelId)
  }

  private fun getProjectModelId(projectModel: ProjectModel, modelClass: Class<*>): GradleModelId {
    val modelId = GradleModelId.createProjectModelId(projectModel, modelClass)
    return applyBuildIdMapping(modelId)
  }

  override fun toString(): String {
    return buildString {
      append("Models{\n")
      append("rootModel=$rootBuild\n")
      append("myModelsById=\n")
      for ((modelId, model) in models) {
        append("$modelId=$model\n")
      }
      append("}")
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GradleIdeaModelHolder::class.java)
  }
}
