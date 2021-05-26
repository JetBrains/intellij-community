// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
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
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val fileEditor = file.getUserData(EDITOR_KEY)
    return if (fileEditor != null) fileEditor else {
      val newEditor = HTMLFileEditor(project, file as LightVirtualFile, file.getUserData(AFFINITY_KEY)!!)
      file.putUserData(EDITOR_KEY, newEditor)
      newEditor
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean =
    JBCefApp.isSupported() && isHTMLEditor(file)

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val AFFINITY_KEY: Key<String> = Key.create("html.editor.affinity.key")
    private val EDITOR_KEY: Key<FileEditor> = Key.create("html.editor.component.key")

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, @DetailedDescription html: String) {
      val file = LightVirtualFile(title, html)
      file.putUserData(AFFINITY_KEY, "")
      FileEditorManager.getInstance(project).openFile(file, true)
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, url: String, @DetailedDescription timeoutHtml: String? = null) {
      val file = LightVirtualFile(title, timeoutHtml ?: "")
      file.putUserData(AFFINITY_KEY, url)
      FileEditorManager.getInstance(project).openFile(file, true)
    }

    @JvmStatic
    fun isHTMLEditor(file: VirtualFile): Boolean =
      file.getUserData(AFFINITY_KEY) != null
  }
}
