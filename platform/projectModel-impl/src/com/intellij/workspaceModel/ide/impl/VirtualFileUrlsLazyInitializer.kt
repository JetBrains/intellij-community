// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VirtualFileUrlsLazyInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val urls = (workspaceModel.getVirtualFileUrlManager() as? IdeVirtualFileUrlManagerImpl)?.getCachedVirtualFileUrls()
               ?: return
    withContext(Dispatchers.IO) {
      blockingContext {
        urls.forEach { (it as? VirtualFileUrlBridge)?.isValid }
      }
    }
  }
}