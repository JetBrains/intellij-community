// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

private class EncodingProjectManagerStartUpActivity : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    // do not try to init on EDT due to VFS usage in loadState
    val service = EncodingProjectManager.getInstance(project) as EncodingProjectManagerImpl
    ApplicationManager.getApplication().invokeLater(
      { service.reloadAlreadyLoadedDocuments() }, project.disposed)
  }
}