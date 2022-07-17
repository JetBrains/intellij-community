// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp

class HTMLEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    file.getUserData(EDITOR_KEY) ?:
    HTMLFileEditor(project, file as LightVirtualFile, file.getUserData(AFFINITY_KEY)!!)
      .also { file.putUserData(EDITOR_KEY, it) }

  override fun accept(project: Project, file: VirtualFile): Boolean =
    JBCefApp.isSupported() && isHTMLEditor(file)

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    val AFFINITY_KEY: Key<String> = Key.create("html.editor.affinity.key")
    val EDITOR_KEY: Key<FileEditor> = Key.create("html.editor.component.key")

    @JvmStatic
    @Suppress("DEPRECATION")
    fun openEditor(project: Project, @DialogTitle title: String, @DetailedDescription html: String) {
      HTMLEditorProviderManager.getInstance(project).openEditor(title, html)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun openEditor(project: Project, @DialogTitle title: String, url: String, @DetailedDescription timeoutHtml: String? = null) {
      HTMLEditorProviderManager.getInstance(project).openEditor(title,url, timeoutHtml)
    }

    @JvmStatic
    fun isHTMLEditor(file: VirtualFile): Boolean =
      file.getUserData(AFFINITY_KEY) != null
  }
}
