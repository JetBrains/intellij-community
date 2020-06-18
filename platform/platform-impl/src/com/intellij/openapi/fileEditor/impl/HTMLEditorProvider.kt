// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class HTMLEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    HTMLFileEditor(file.getUserData(URL_KEY), file.getUserData(HTML_KEY))

  override fun accept(project: Project, file: VirtualFile) = file.getUserData(URL_KEY) != null

  override fun getEditorTypeId() = "html-editor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val URL_KEY: Key<String> = Key.create("URL_KEY")
    private val HTML_KEY: Key<String> = Key.create("HTML_KEY")

    fun openEditor(project: Project, title: String, url: String? = null, html: String? = null) {
      val file = LightVirtualFile(title)
      if (url != null) { file.apply { putUserData(URL_KEY, url) } }
      if (html != null) { file.apply { putUserData(HTML_KEY, html) } }

      FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun isHTMLEditor(file: VirtualFile) = file.getUserData(URL_KEY) != null || file.getUserData(HTML_KEY) != null
  }
}