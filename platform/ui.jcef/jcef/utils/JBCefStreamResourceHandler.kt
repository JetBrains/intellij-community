// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.IOException
import java.io.InputStream

/**
 * A handler for managing custom resource requests in JCEF.
 * This class implements the `CefResourceHandler` interface, enabling the customization of resource
 * loading from a provided input stream. It supports setting MIME types and headers for responses
 * and ensures proper disposal of resources.
 *
 * @param myStream The input stream from which the resource will be read.
 * @param myMimeType The MIME type of the response to be sent to the client.
 * @param parent A `Disposable` object to which this handler is registered for automatic disposal.
 * @param headers Optional headers that will be included in the response.
 */
open class JBCefStreamResourceHandler(
  private val myStream: InputStream,
  private val myMimeType: String,
  parent: Disposable,
  private val headers: Map<String, String> = mapOf(),
) : CefResourceHandler, Disposable {
  init {
    Disposer.register(parent, this)
  }

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    callback.Continue()
    return true
  }

  override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
    response.mimeType = myMimeType
    response.status = 200
    for (header in headers) {
      response.setHeaderByName(header.key, header.value, true /* overwrite */)
    }
  }

  override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
    try {
      bytesRead.set(myStream.read(dataOut, 0, bytesToRead))
      if (bytesRead.get() != -1) {
        return true
      }
    }
    catch (_: IOException) {
      callback.cancel()
    }
    bytesRead.set(0)
    Disposer.dispose(this)
    return false
  }

  override fun cancel() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    try {
      myStream.close()
    }
    catch (e: IOException) {
      Logger.getInstance(JBCefStreamResourceHandler::class.java).warn("Failed to close the stream", e)
    }
  }
}