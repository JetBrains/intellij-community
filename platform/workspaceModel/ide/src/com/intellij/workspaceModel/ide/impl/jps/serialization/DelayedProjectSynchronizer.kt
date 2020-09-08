// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeProjectLifecycleListener
import kotlin.system.measureTimeMillis

class DelayedProjectSynchronizer : StartupActivity {
  override fun runActivity(project: Project) {
    if (LegacyBridgeProjectLifecycleListener.enabled(project)
        && (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
      val loadingTime = measureTimeMillis {
        JpsProjectModelSynchronizer.getInstance(project)?.loadRealProject(project)
      }
      log.info("Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}")
    }
  }

  companion object {
    private val log = logger<DelayedProjectSynchronizer>()
  }
}