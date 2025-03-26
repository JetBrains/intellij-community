// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.google.gson.GsonBuilder
import com.intellij.build.SyncViewManager
import com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel.GradleDependencyNodeDeserializer
import com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel.GradleDependencyReportTask
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.loadTaskInitScript
import org.jetbrains.plugins.gradle.service.execution.toGroovyListLiteral
import org.jetbrains.plugins.gradle.service.execution.toGroovyStringLiteral
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.readBytes

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GradleDependencyNodeIndex(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  fun getOrCollectDependencies(moduleData: GradleModuleData, configurations: List<String>): CompletableFuture<List<DependencyScopeNode>> {
    return coroutineScope.async {
      collectDependencies(moduleData, configurations)
    }.asCompletableFuture()
  }

  private suspend fun collectDependencies(moduleData: GradleModuleData, configurations: List<String>): List<DependencyScopeNode> {
    val outputFile = FileUtil.createTempFile("dependencies", ".json", true).toPath()
    try {
      val taskConfiguration = """
          |outputFile = project.file("${outputFile.toCanonicalPath()}")
          |configurations = ${configurations.toGroovyListLiteral { toGroovyStringLiteral() }}
        """.trimMargin()

      val future = CompletableFuture<Boolean>()
      val taskName = GradleDependencyReportTask::class.java.getSimpleName()
      val taskType = GradleDependencyReportTask::class.java.getName()
      val taskPath = moduleData.gradleIdentityPath.removeSuffix(":") + ":" + taskName
      val tools = setOf(GradleDependencyReportTask::class.java, GsonBuilder::class.java)
      val initScript = loadTaskInitScript(moduleData.gradleIdentityPath, taskName, taskType, tools, taskConfiguration)
      ExternalSystemUtil.runTask(
        TaskExecutionSpec.create()
          .withProject(project)
          .withSystemId(GradleConstants.SYSTEM_ID)
          .withSettings(ExternalSystemTaskExecutionSettings().also {
            it.executionName = GradleBundle.message("gradle.dependency.analyzer.loading")
            it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
            it.externalProjectPath = moduleData.directoryToRunTask
            it.taskNames = listOf(taskPath)
          })
          .withUserData(UserDataHolderBase().also {
            it.putUserData(GradleTaskManager.INIT_SCRIPT_KEY, initScript)
            it.putUserData(ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY, SyncViewManager::class.java)
          })
          .withCallback(future)
          .build()
      )

      if (!future.await()) {
        // The standard Build tool window will be shown with the problem on the Gradle side
        return emptyList()
      }

      val json = outputFile.readBytes()
      return GradleDependencyNodeDeserializer.fromJson(json)
    }
    finally {
      Files.delete(outputFile)
    }
  }

  companion object {

    @JvmStatic
    fun getOrCollectDependencies(project: Project, moduleData: GradleModuleData, configurations: List<String>): CompletableFuture<List<DependencyScopeNode>> {
      return project.service<GradleDependencyNodeIndex>()
        .getOrCollectDependencies(moduleData, configurations)
    }
  }
}