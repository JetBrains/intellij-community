// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class VirtualFileUrlsLazyInitializer: ProjectActivity {
  override suspend fun execute(project: Project) {
    val urls = (VirtualFileUrlManager.getInstance(project) as? IdeVirtualFileUrlManagerImpl)?.getCachedVirtualFileUrls() ?: return
    withContext(Dispatchers.IO) {
      urls.forEach { (it as? VirtualFileUrlBridge)?.isValid }
    }
  }
}