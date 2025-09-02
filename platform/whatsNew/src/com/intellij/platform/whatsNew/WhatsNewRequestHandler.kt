// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.ResourceHandler
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.ResourceHandler.ResourceResponse.NotFound
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.whatsNew.WhatsNewVisionContent.Companion.LOCALHOST
import com.intellij.platform.whatsNew.WhatsNewVisionContent.Companion.WHATS_NEW_VISION_SCHEME
import java.io.InputStream
import kotlin.io.path.extension

class WhatsNewRequestHandler(val contentProvider: WhatsNewInVisionContentProvider) : ResourceHandler {

  override fun shouldInterceptRequest(request: ResourceHandler.ResourceRequest): Boolean {
    val uri = request.uri
    return uri.scheme.equals(WHATS_NEW_VISION_SCHEME) && uri.host.equals(LOCALHOST)
  }

  override suspend fun handleResourceRequest(request: ResourceHandler.ResourceRequest): ResourceHandler.ResourceResponse {
    val resourceName = request.uri.path.trim('/')
    val contentResource = contentProvider.getWhatsNewResource(resourceName)
    if (!contentResource.checkAvailability())
      return NotFound

    val mimeType = getMIMEType(request.uri.path.toNioPathOrNull()?.extension ?: "")
    val resource = object : ResourceHandler.Resource {
      override val mimeType: String
        get() = mimeType

      override suspend fun getResourceStream(): InputStream? = contentResource.openStream()
    }

    return ResourceHandler.ResourceResponse.HandleResource(resource)
  }
}

private val logger = logger<WhatsNewRequestHandler>()

private fun getMIMEType(extension: String): String {
  return when (extension) {
    "gif" -> "image/gif"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "svg" -> "image/svg+xml"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "webp" -> "image/webp"
    else -> "".also { logger.warn("Unknown file extension: '$extension' for MIME type. Falling back to '$it'.") }
  }
}