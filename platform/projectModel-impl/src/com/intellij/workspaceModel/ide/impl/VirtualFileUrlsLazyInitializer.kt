// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VirtualFileUrlsLazyInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val urls = (workspaceModel.getVirtualFileUrlManager() as? VirtualFileUrlManagerEx)?.getCachedVirtualFileUrls()
               ?: return
    withContext(Dispatchers.IO) {
      urls.forEach { (it as? VirtualFilePointer)?.isValid }
    }
  }
}