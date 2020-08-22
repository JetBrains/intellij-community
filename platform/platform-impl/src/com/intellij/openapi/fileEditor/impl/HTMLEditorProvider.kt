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
    HTMLFileEditor(file.getUserData(URL_KEY), file.getUserData(HTML_KEY), file.getUserData(TIMEOUT_CALLBACK))

  override fun accept(project: Project, file: VirtualFile) = isHTMLEditor(file)

  override fun getEditorTypeId() = "html-editor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val URL_KEY: Key<String> = Key.create("URL_KEY")
    private val HTML_KEY: Key<String> = Key.create("HTML_KEY")

    val TIMEOUT_CALLBACK: Key<String> = Key.create("TIMEOUT_CALLBACK")

    fun openEditor(project: Project, title: String, url: String? = null, html: String? = null, timeoutCallback: String? = null) {
      val file = LightVirtualFile(title)
      if (url == null && html == null) return
      if (url != null) { file.putUserData(URL_KEY, url) }
      if (html != null) { file.putUserData(HTML_KEY, html) }
      if (timeoutCallback != null) { file.putUserData(TIMEOUT_CALLBACK, timeoutCallback) }

      FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun isHTMLEditor(file: VirtualFile) = file.getUserData(URL_KEY) != null || file.getUserData(HTML_KEY) != null
  }
}