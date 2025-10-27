// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.HttpRequestHandler
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * Created by Egor.Malyshev on 7/13/2017.
 */
abstract class HelpRequestHandlerBase : HttpRequestHandler() {
  @NonNls
  open val prefix: String = "/help/"
  val supportedLocales: Set<String> = setOf("zh-cn")

  override fun isAccessible(request: HttpRequest): Boolean {
    return super.isAccessible(request) && request.uri().contains(prefix)
  }

  fun getRequestLocalePath(request: HttpRequest): String {
    val requestLocale = request.uri().substringBefore(prefix).substringAfter("/")
    return if (supportedLocales.contains(requestLocale)) requestLocale else ""
  }

  protected fun sendResource(
    resourceName: String,
    resourceLocation: String,
    request: FullHttpRequest,
    channel: Channel,
    extraHeaders: HttpHeaders,
  ): Boolean {

    val isImage = resourceLocation.contains("/img/")

    //We don't ship dark images because of storage concerns, yet frontend might still request them, and to avoid 404 we provide fallback
    val retrieveName = when (isImage) {
      true -> {
        val base = Path(resourceName)
        val baseName = base.nameWithoutExtension
        """${baseName.substringBeforeLast("_dark")}.${base.extension}"""
      }
      //For non-images it's just a base name
      else -> resourceName
    }

    val content = Utils.getResourceWithFallback(if (isImage) "images" else "topics",
                                                retrieveName, getRequestLocalePath(request))
    return sendData(
      content ?: throw Exception("$resourceName not found in $resourceLocation via ${request.uri()}"), resourceName,
      request, channel,
      extraHeaders
    )
  }
}
