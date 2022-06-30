// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture
import kotlin.system.measureTimeMillis

/**
 * Loading the real state of the project after loading from cache.
 *
 * Initially IJ loads the state of workspace model from the cache. In this startup activity it synchronizes the state
 * of workspace model with project model files (iml/xml).
 */
class DelayedProjectSynchronizer : StartupActivity.Background {
  @OptIn(DelicateCoroutinesApi::class)
  override fun runActivity(project: Project) {
    // todo remove GlobalScope usage, platform should support suspend activities
    GlobalScope.launch {
      if (project.isDisposed) {
        return@launch
      }

      doSync(project)
    }
  }

  companion object {
    private suspend fun doSync(project: Project) {
      val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project) ?: return
      if (!(WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
        return
      }

      val loadingTime = measureTimeMillis {
        projectModelSynchronizer.loadProject(project)
        project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
      }
      logger<DelayedProjectSynchronizer>().info(
        "Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}"
      )
    }

    @TestOnly
    suspend fun backgroundPostStartupProjectLoading(project: Project) {
      // Due to making [DelayedProjectSynchronizer] as backgroundPostStartupActivity we should have this hack because
      // background activity doesn't start in the tests
      val allActivitiesPassedFuture = StartupManagerEx.getInstanceEx(project).allActivitiesPassedFuture as CompletableFuture<*>
      allActivitiesPassedFuture.asDeferred().join()
      doSync(project)
    }
  }
}