// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlin.system.measureTimeMillis

/**
 * Loading the real state of the project after loading from cache.
 *
 * Initially IJ loads the state of workspace model from the cache. In this startup activity it synchronizes the state
 * of workspace model with project model files (iml/xml).
 */
class DelayedProjectSynchronizer : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (WorkspaceModel.isEnabled) {
      val projectModelSynchronizer = JpsProjectModelSynchronizer.getInstance(project)
      if (projectModelSynchronizer != null && (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
        val loadingTime = measureTimeMillis {
          projectModelSynchronizer.loadRealProject(project)
          project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
        }
        log.info("Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}")
      }
    }
  }

  companion object {
    private val log = logger<DelayedProjectSynchronizer>()
  }
}