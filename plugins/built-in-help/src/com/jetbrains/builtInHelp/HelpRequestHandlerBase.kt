// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.intellij.util.ResourceUtil
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import org.apache.commons.compress.utils.FileNameUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.HttpRequestHandler

/**
 * Created by Egor.Malyshev on 7/13/2017.
 */
abstract class HelpRequestHandlerBase : HttpRequestHandler() {
  @NonNls
  open val prefix: String = "/help/"

  override fun isAccessible(request: HttpRequest): Boolean {
    return super.isAccessible(request) && request.uri().contains(prefix)
  }

  protected fun sendResource(
    resourceName: String,
    resourceLocation: String,
    request: FullHttpRequest,
    channel: Channel,
    extraHeaders: HttpHeaders
  ): Boolean {

    val isImage = resourceLocation.contains("/img/")
    val retrieveName = when (isImage) {
      true -> {
        val baseName = FileNameUtils.getBaseName(resourceName)
        """${baseName.substringBeforeLast("_dark")}.${FileNameUtils.getExtension(resourceName)}"""
      }
      else -> resourceName
    }
    val resStream = ResourceUtil.getResourceAsStream(
      HelpRequestHandlerBase::class.java.classLoader,
      if (isImage) "images" else "topics", retrieveName
    )
    return sendData(
      resStream?.readAllBytes() ?: throw Exception("$resourceName not found in $resourceLocation via ${request.uri()}"), resourceName,
      request, channel,
      extraHeaders
    )
  }
}
