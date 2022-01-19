// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import org.jetbrains.annotations.TestOnly
import kotlin.system.measureTimeMillis

/**
 * Loading the real state of the project after loading from cache.
 *
 * Initially IJ loads the state of workspace model from the cache. In this startup activity it synchronizes the state
 * of workspace model with project model files (iml/xml).
 */
class DelayedProjectSynchronizer : StartupActivity.Background {
  override fun runActivity(project: Project) {
    val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project) ?: return
    if ((WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
      val loadingTime = measureTimeMillis {
        projectModelSynchronizer.loadProject(project)
        project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
      }
      logger<DelayedProjectSynchronizer>().info(
        "Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}"
      )
    }
  }

  companion object {
    @TestOnly
    fun backgroundPostStartupProjectLoading(project: Project) {
      // Due to making [DelayedProjectSynchronizer] as backgroundPostStartupActivity we should have this hack because
      // background activity doesn't start in the tests
      val startupManager = StartupManager.getInstance(project)
      while (!startupManager.postStartupActivityPassed()) { }
      val extensionPointName = ExtensionPointName<StartupActivity.Background>("com.intellij.backgroundPostStartupActivity")
      extensionPointName.extensionList.filterIsInstance<DelayedProjectSynchronizer>().single().runActivity(project)
    }
  }
}