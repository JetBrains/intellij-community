// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.browsers.actions.WebPreviewFileType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.JS_FUNCTION_NAME
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp

class HTMLEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    file.getUserData(EDITOR_KEY) ?:
    HTMLFileEditor(project, file as LightVirtualFile, REQUEST_KEY.get(file)!!).also { file.putUserData(EDITOR_KEY, it) }

  override fun accept(project: Project, file: VirtualFile): Boolean =
    JBCefApp.isSupported() && file.getUserData(REQUEST_KEY) != null

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  companion object {
    private val REQUEST_KEY: Key<Request> = Key.create("html.editor.request.key")
    private val EDITOR_KEY: Key<FileEditor> = Key.create("html.editor.component.key")

    const val JS_FUNCTION_NAME: String = "jbCefQuery"

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, html: String) {
      openEditor(project, title, Request.html(html))
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, url: String, timeoutHtml: String? = null) {
      openEditor(project, title, Request.url(url).withTimeoutHtml(timeoutHtml))
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, request: Request): FileEditor? {
      logger<HTMLEditorProvider>().info(if (request.url != null) "URL=${request.url}" else "HTML (${request.html!!.length} chars)")
      val file = LightVirtualFile(title, WebPreviewFileType.INSTANCE, "")
      REQUEST_KEY.set(file, request)
      return FileEditorManager.getInstance(project)
        .openFile(file, true)
        .find { it is HTMLFileEditor }
    }
  }

  class Request private constructor(internal val html: String?, internal val url: String?) {
    internal var timeoutHtml: String? = null; private set
    internal var queryHandler: JsQueryHandler? = null; private set

    fun withTimeoutHtml(timeoutHtml: String?): Request {
      this.timeoutHtml = timeoutHtml
      return this
    }

    fun withQueryHandler(queryHandler: JsQueryHandler?): Request {
      this.queryHandler = queryHandler
      return this
    }

    companion object {
      @JvmStatic fun html(html: String): Request = Request(html, null)
      @JvmStatic fun url(url: String): Request = Request(null, url)
    }
  }

  /**
   * The interface allows a loaded HTML page to interact with the IDE.
   * It is exposed to the page as a JavaScript function named "jbCefQuery" (see [JS_FUNCTION_NAME]) via the `window` object:
   * ```
   * <script>
   *   function sendPingRequest() {
   *     window.jbCefQuery({
   *       request: 'ping-the-IDE',
   *       onSuccess: function(response) { /*...*/ },
   *       onFailure: function(errCode, errMsg) { /*...*/ }
   *     });
   *   }
   * </script>
   *
   * <button onclick="sendPingRequest()">Ping it</button>
   * ```
   *
   * **Note to implementers**: as there is no way to verify whether the request was initiated by a user,
   * please make sure the listener doesn't perform any potentially dangerous action.
   */
  interface JsQueryHandler {
    suspend fun query(id: Long, request: String): String
  }
}
