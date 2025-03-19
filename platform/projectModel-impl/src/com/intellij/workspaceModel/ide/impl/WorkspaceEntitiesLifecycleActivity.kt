// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private class WorkspaceEntitiesLifecycleActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    coroutineScope {
      WorkspaceEntityLifecycleSupporter.EP_NAME.addExtensionPointListener(this, object : ExtensionPointListener<WorkspaceEntityLifecycleSupporter<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>>> {
        override fun extensionAdded(extension: WorkspaceEntityLifecycleSupporter<out WorkspaceEntity, out WorkspaceEntity.Builder<out WorkspaceEntity>>, pluginDescriptor: PluginDescriptor) {
          launch {
            WorkspaceEntityLifecycleSupporterUtils.ensureEntitiesInWorkspaceAreAsProviderDefined(project, extension)
          }
        }
      })

      WorkspaceEntityLifecycleSupporterUtils.ensureAllEntitiesInWorkspaceAreAsProvidersDefined(project)

      awaitCancellation()
    }
  }
}