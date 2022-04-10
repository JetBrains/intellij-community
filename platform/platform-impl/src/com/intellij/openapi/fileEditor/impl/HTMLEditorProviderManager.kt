// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.browsers.actions.WebPreviewFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
@Deprecated("Use com.intellij.openapi.fileEditor.impl.HTMLEditorProvider")
open class HTMLEditorProviderManager(protected val project: Project) {
  companion object {
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getInstance(project: Project) = project.service<HTMLEditorProviderManager>()
  }

  open fun openEditor(@NlsContexts.DialogTitle title: String, @NlsContexts.DetailedDescription html: String) {
    val file = LightVirtualFile(title, WebPreviewFileType.INSTANCE, html)
    file.putUserData(HTMLEditorProvider.AFFINITY_KEY, "")
    FileEditorManager.getInstance(project).openFile(file, true)
  }

  open fun openEditor(@NlsContexts.DialogTitle title: String, url: String, @NlsContexts.DetailedDescription timeoutHtml: String? = null) {
    val file = LightVirtualFile(title, WebPreviewFileType.INSTANCE, timeoutHtml ?: "")
    file.putUserData(HTMLEditorProvider.AFFINITY_KEY, url)
    FileEditorManager.getInstance(project).openFile(file, true)
  }
}
