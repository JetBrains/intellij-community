// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.whatsNew.WhatsNewVisionContent.Companion.LOCALHOST
import com.intellij.platform.whatsNew.WhatsNewVisionContent.Companion.WHATS_NEW_VISION_SCHEME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.InputStream
import java.net.URI
import kotlin.io.path.extension

class WhatsNewRequestHandler(val contentProvider: WhatsNewInVisionContentProvider, val parentCoroutineScope: CoroutineScope) : CefRequestHandlerAdapter() {
  private val REQUEST_HANDLER = object : CefResourceRequestHandlerAdapter() {
    override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler? {
      return WhatsNewResourceHandler(contentProvider, parentCoroutineScope)
    }
  }

  override fun getResourceRequestHandler(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    isNavigation: Boolean,
    isDownload: Boolean,
    requestInitiator: String?,
    disableDefaultHandling: BoolRef?,
  ): CefResourceRequestHandler? {
    val uri = request?.url?.toURIOrNull() ?: return null
    if (!uri.scheme.equals(WHATS_NEW_VISION_SCHEME) || !uri.host.equals(LOCALHOST))
      return null

    return REQUEST_HANDLER
  }
}

class WhatsNewResourceHandler(resourceContentSourceProvider: WhatsNewInVisionContentProvider, parentCoroutineScope: CoroutineScope) : CefResourceHandlerAdapter() {
  lateinit var uri: URI
  var myStream: InputStream? = null
  var resource: ContentSource? = null
  var provider: WhatsNewInVisionContentProvider? = resourceContentSourceProvider

  val coroutineScope: CoroutineScope = parentCoroutineScope.childScope("WhatsNewResourceHandler")

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    uri = request.url.toURIOrNull() ?: return false.also { callback.cancel() }
    coroutineScope.launch {
      logger.runAndLogException {
        val resourceName = uri.path.trim('/').removePrefix(LOCALHOST).trim('/') // TODO: Sometimes localhost is duplicated
        resource = provider?.getWhatsNewResource(resourceName)
        if (resource == null || resource?.checkAvailability() != true)
          return@launch callback.cancel()
      } ?: return@launch callback.cancel()
      callback.Continue()
    }
    return true
  }

  override fun getResponseHeaders(response: CefResponse, responseLength: IntRef?, redirectUrl: StringRef?) {
    response.mimeType = getMIMEType(uri.path.toNioPathOrNull()?.extension ?: "")
    response.status = 200
  }

  override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
    if (myStream == null) {
      if (resource == null) return false.also { callback.cancel() }

      bytesRead.set(0)
      coroutineScope.launch {
        myStream = logger.runAndLogException { resource?.openStream() } ?: return@launch callback.cancel()
        callback.Continue()
      }
      return true
    }

    try {
      bytesRead.set(myStream!!.read(dataOut, 0, bytesToRead))
      if (bytesRead.get() != -1) {
        return true
      }
    }
    catch (e: Exception) {
      logger.error(e)
      callback.cancel()
    }
    bytesRead.set(0)
    return false
  }

  override fun cancel() {
    provider = null
    logger.runAndLogException {
      myStream?.close()
    }
    coroutineScope.cancel()
  }
}

private fun String.toURIOrNull() = runCatching { URI(this) }.getOrLogException { logger.trace(it) }

private val logger = logger<WhatsNewRequestHandler>()

private fun getMIMEType(extension: String): String {
  return when (extension) {
    "gif" -> "image/gif"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "svg" -> "image/svg+xml"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "application/octet-stream".also { logger.warn("Unknown file extension: '$extension' for MIME type. Falling back to '$it'.") }
  }
}