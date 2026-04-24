// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.browsers.actions.WebPreviewFileType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Companion.JS_FUNCTION_NAME
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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
      return openEditor(project, title, request, WebPreviewFileType.INSTANCE)
    }

    @JvmStatic
    fun openEditor(project: Project, @DialogTitle title: String, request: Request, fileType: FileType): FileEditor? {
      return openEditor(project, title, request, fileType, ignoreJcef = false)
    }

    @TestOnly
    @ApiStatus.Internal
    @JvmStatic
    fun openEditorWithoutJcef(project: Project, @DialogTitle title: String, request: Request, fileType: FileType): FileEditor? {
      return openEditor(project, title, request, fileType, ignoreJcef = true)
    }

    /**
     * Opens an HTML page in the editor in a suspending way
     */
    @ApiStatus.Experimental
    suspend fun openEditorAsync(project: Project, @DialogTitle title: String, request: Request): FileEditor? {
      val file = HTMLVirtualFile.createFile(project, title, request, WebPreviewFileType.INSTANCE, ignoreJcef = false)
      val fileEditorManager = FileEditorManager.getInstance(project)
      val fileEditors = if (fileEditorManager is FileEditorManagerEx) {
        fileEditorManager.openFile(file, FileEditorOpenOptions(requestFocus = true, waitForCompositeOpen = false))
          .allEditorsWithProviders
          .map { it.fileEditor }
      } else {
        withContext(Dispatchers.EDT) {
          FileEditorManager.getInstance(project).openFile(file, true).toList()
        }
      }
      return fileEditors.find { it is HTMLFileEditor }
    }

    /**
     * Sends a request for opening an HTML page some time later. Does not block EDT.
     * This is intended for usage in Java and non-suspend Kotlin.
     */
    @ApiStatus.Experimental
    @JvmStatic
    fun openEditorWithoutBlocking(project: Project, @DialogTitle title: String, request: Request) {
      project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch { openEditorAsync(project, title, request) }
    }

    @JvmStatic
    private fun openEditor(
      project: Project,
      title: @DialogTitle String,
      request: Request,
      fileType: FileType,
      ignoreJcef: Boolean,
    ): FileEditor? {
      logger<HTMLEditorProvider>().info(if (request.url == null) "HTML (${request.html!!.length} chars)" else "URL=${request.url}")
      val file = HTMLVirtualFile.createFile(project, title, request, fileType, ignoreJcef)
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
    require(!file.isDisposed()) {
      "html request is already disposed"
    }
    return if (file.shouldUseMockEditor()) {
      HTMLFileEditorMock(file)
    }
    else {
      HTMLFileEditorImpl(project, file, file.htmlRequest)
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is HTMLVirtualFile && !file.isDisposed() && file.isJcefSupported()
  }

  override fun acceptRequiresReadAction(): Boolean = false

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  class Request private constructor(@JvmField internal val html: String?, @JvmField internal val url: String?) {
    internal var currentUrl: String? = url
    internal var timeoutHtml: String? = null
      private set
    internal var queryHandler: JsQueryHandler? = null
      private set
    internal var requestHandler: ResourceHandler? = null; private set
    internal var onUrlChanged: (String?, String) -> Unit = { _, _ -> }
      private set

    companion object {
      @JvmOverloads
        /**
         * Creates a Request object with the provided HTML content.
         *
         * @param html The HTML content to display in the editor.
         * @param url A synthetic URL that will be observable from inside the page's JavaScript.
         *            This is not the actual URL where content will be loaded from, but rather
         *            a value that can be accessed by scripts running in the page.
         *            It can be used for routing or state management within the HTML.
         * @return A new Request instance configured with the provided HTML content and URL.
         */
      fun html(html: String, url: String? = null): Request = Request(html = html, url = url)

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

    @ApiStatus.Internal
    fun withOnUrlChanged(onUrlChanged: (String?, String) -> Unit): Request {
      this.onUrlChanged = onUrlChanged
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
  interface HTMLFileEditor : FileEditor

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
