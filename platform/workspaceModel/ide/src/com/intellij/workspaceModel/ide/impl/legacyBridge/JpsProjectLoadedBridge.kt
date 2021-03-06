// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.WorkspaceModel

/**
 * Bridge to support [JpsProjectLoadedListener.LOADED] topic on the old project model
 */
class JpsProjectLoadedBridge : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (WorkspaceModel.isEnabled) return
    project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
  }
}
