// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.platform.workspace.storage.WorkspaceEntity

class WorkspaceEntitiesLifecycleActivity : ProjectActivity {
  override suspend fun execute(project: Project) {

    WorkspaceEntityLifecycleSupporter.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<WorkspaceEntityLifecycleSupporter<out WorkspaceEntity>> {
      override fun extensionAdded(extension: WorkspaceEntityLifecycleSupporter<out WorkspaceEntity>, pluginDescriptor: PluginDescriptor) {
        WorkspaceEntityLifecycleSupporterUtils.ensureEntitiesInWorkspaceAreAsProviderDefined(project, extension)
      }
    }, project.getService(ConstantEntitiesDisposableService::class.java))

    WorkspaceEntityLifecycleSupporterUtils.ensureAllEntitiesInWorkspaceAreAsProvidersDefined(project)
  }
}

@Service(Service.Level.PROJECT)
class ConstantEntitiesDisposableService : Disposable {
  override fun dispose() {}
}
