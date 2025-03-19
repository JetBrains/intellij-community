// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class EncodingProjectManagerStartUpActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // do not try to init on EDT due to VFS usage in loadState
    val service = project.serviceAsync<EncodingProjectManager>() as EncodingProjectManagerImpl
    withContext(Dispatchers.EDT) {
      service.reloadAlreadyLoadedDocuments()
    }
  }
}