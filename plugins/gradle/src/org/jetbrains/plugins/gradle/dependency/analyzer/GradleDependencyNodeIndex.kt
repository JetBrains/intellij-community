// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.build.SyncViewManager
import com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel.GradleDependencyNodeDeserializer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex.getDataStorageCachedValue
import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.psi.util.CachedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.loadCollectDependencyInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

private typealias DependencyNodes = List<DependencyScopeNode>
private typealias DependencyNodeCache = ConcurrentHashMap<String, AsyncLocalCache<DependencyNodes>>

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GradleDependencyNodeIndex(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  /**
   * Only one dependency collection action is allowed to be executed at the same moment.
   * It is necessary to reduce the number of working Gradle daemons in parallel.
   */
  private val mutex = Mutex()

  fun getOrCollectDependencies(moduleData: GradleModuleData): CompletableFuture<DependencyNodes> {
    return coroutineScope.async {
      val cache = getDataStorageCachedValue(project, project, DEPENDENCY_NODE_KEY) { DependencyNodeCache() }
      val localCache = cache.computeIfAbsent(moduleData.gradleProjectDir) { AsyncLocalCache() }
      localCache.getOrCreateValue(0) { // async lazy initialisation
        mutex.withLock {
          collectDependencies(moduleData)
        }
      }
    }.asCompletableFuture()
  }

  private suspend fun collectDependencies(moduleData: GradleModuleData): DependencyNodes {
    val eel = project.getEelDescriptor().upgrade()
    val taskOutputEelPath = eel.fs.createTemporaryFile()
      .prefix("$TASK_NAME-output")
      .getOrThrow()
    val taskOutputPath = taskOutputEelPath.asNioPath()
    try {
      val future = CompletableFuture<Boolean>()
      val taskPath = moduleData.gradleIdentityPath.removeSuffix(":") + ":" + TASK_NAME
      val initScript = loadCollectDependencyInitScript(TASK_NAME, taskOutputEelPath)
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

      val json = taskOutputPath.readBytes()
      return GradleDependencyNodeDeserializer.fromJson(json)
    }
    finally {
      taskOutputPath.deleteIfExists()
    }
  }

  companion object {

    private const val TASK_NAME = "ijCollectDependencies"

    private val DEPENDENCY_NODE_KEY = Key.create<CachedValue<DependencyNodeCache>>("GradleDependencyNodeIndex")

    @JvmStatic
    fun getOrCollectDependencies(project: Project, moduleData: GradleModuleData): CompletableFuture<DependencyNodes> {
      return project.service<GradleDependencyNodeIndex>()
        .getOrCollectDependencies(moduleData)
    }
  }
}