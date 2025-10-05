// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.JS_FUNCTION_NAME
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.net.URI

class HTMLEditorProvider : FileEditorProvider, DumbAware {
  @Suppress("CompanionObjectInExtension")
  companion object {
    const val JS_FUNCTION_NAME: String = "jbCefQuery"

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, html: String) {
      openEditor(project = project, title = title, request = Request.html(html))
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, url: String, timeoutHtml: String? = null) {
      openEditor(project = project, title = title, request = Request.url(url).withTimeoutHtml(timeoutHtml))
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, request: Request): FileEditor? {
      logger<HTMLEditorProvider>().info(if (request.url == null) "HTML (${request.html!!.length} chars)" else "URL=${request.url}")
      val file = HTMLVirtualFile.createFile(project, title, request)
      return FileEditorManager.getInstance(project)
        .openFile(file, true)
        .find { it is HTMLFileEditor }
    }
  }

  @ApiStatus.Internal
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    require(file is HTMLVirtualFile) {
      "cannot create html editor for non-html file, actual $file"
    }
    return file.createEditor(project)
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return JBCefApp.isSupported() && file is HTMLVirtualFile && !file.isDisposed()
  }

  override fun acceptRequiresReadAction(): Boolean = false

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  class Request private constructor(@JvmField internal val html: String?, @JvmField internal val url: String?) {
    internal var timeoutHtml: String? = null
      private set
    internal var queryHandler: JsQueryHandler? = null
      private set
    internal var requestHandler: ResourceHandler? = null; private set

    companion object {
      @JvmStatic
      fun html(html: String): Request = Request(html = html, url = null)

      @JvmStatic
      fun url(url: String): Request = Request(html = null, url = url)
    }

    fun withTimeoutHtml(timeoutHtml: String?): Request {
      this.timeoutHtml = timeoutHtml
      return this
    }

    fun withQueryHandler(queryHandler: JsQueryHandler?): Request {
      this.queryHandler = queryHandler
      return this
    }

    @ApiStatus.Internal
    fun withResourceHandler(requestHandler: ResourceHandler?): Request {
      this.requestHandler = requestHandler
      return this
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

  @ApiStatus.Internal
  interface ResourceHandler {
    fun shouldInterceptRequest(request: ResourceRequest): Boolean
    suspend fun handleResourceRequest(request: ResourceRequest): ResourceResponse

    interface Resource {
      val mimeType: String
      suspend fun getResourceStream(): InputStream?
    }

    data class ResourceRequest(val uri: URI)

    sealed class ResourceResponse {
      data object NotFound : ResourceResponse()
      data class HandleResource(val resource: Resource) : ResourceResponse()
    }
  }
}
