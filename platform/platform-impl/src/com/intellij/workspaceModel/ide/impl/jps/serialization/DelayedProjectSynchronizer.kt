// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import kotlin.system.measureTimeMillis

/**
 * Loading the real state of the project after loading from cache.
 *
 * Initially IJ loads the state of workspace model from the cache. In this startup activity it synchronizes the state
 * of workspace model with project model files (iml/xml).
 */
@VisibleForTesting
class DelayedProjectSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    doSync(project)
  }

  companion object {
    private suspend fun doSync(project: Project) {
      val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
      if (!(WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
        return
      }

      val loadingTime = measureTimeMillis {
        projectModelSynchronizer.loadProject(project)
        project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
      }
      thisLogger().info(
        "Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}"
      )
    }

    @TestOnly
    suspend fun backgroundPostStartupProjectLoading(project: Project) {
      // Due to making [DelayedProjectSynchronizer] as backgroundPostStartupActivity we should have this hack because
      // background activity doesn't start in the tests
      StartupManager.getInstance(project).allActivitiesPassedFuture.join()
      doSync(project)
    }
  }
}