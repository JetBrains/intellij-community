// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    val html = file.getUserData(HTML_KEY)
    val url = file.getUserData(URL_KEY)
    val timeoutHtml = file.getUserData(TIMEOUT_HTML_KEY)
    arrayOf(HTML_KEY, URL_KEY, TIMEOUT_HTML_KEY).forEach { file.putUserData(it, null) }
    return if (html != null) HTMLFileEditor(html) else HTMLFileEditor(url!!, timeoutHtml)
  }

  override fun accept(project: Project, file: VirtualFile): Boolean =
    JBCefApp.isSupported() && isHTMLEditor(file)

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val AFFINITY_KEY: Key<Boolean> = Key.create("html.editor.affinity.key")
    private val HTML_KEY: Key<String> = Key.create("html.editor.html.key")
    private val URL_KEY: Key<String> = Key.create("html.editor.url.key")
    private val TIMEOUT_HTML_KEY: Key<String> = Key.create("html.editor.timeout.text.key")

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, @DetailedDescription html: String) {
      val file = LightVirtualFile(title)
      file.putUserData(AFFINITY_KEY, true)
      file.putUserData(HTML_KEY, html)
      FileEditorManager.getInstance(project).openFile(file, true)
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, url: String, @DetailedDescription timeoutHtml: String? = null) {
      val file = LightVirtualFile(title)
      file.putUserData(AFFINITY_KEY, true)
      file.putUserData(URL_KEY, url)
      file.putUserData(TIMEOUT_HTML_KEY, timeoutHtml)
      FileEditorManager.getInstance(project).openFile(file, true)
    }

    @JvmStatic
    fun isHTMLEditor(file: VirtualFile): Boolean =
      file.getUserData(AFFINITY_KEY) == true
  }
}
