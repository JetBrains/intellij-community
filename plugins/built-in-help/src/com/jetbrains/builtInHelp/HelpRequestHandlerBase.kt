// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import org.apache.commons.compress.utils.IOUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.HttpRequestHandler

/**
 * Created by Egor.Malyshev on 7/13/2017.
 */
abstract class HelpRequestHandlerBase : HttpRequestHandler() {
  @NonNls
  open val prefix: String = "/help/"

  override fun isAccessible(request: HttpRequest): Boolean {
    return super.isAccessible(request) && request.uri().startsWith(prefix)
  }

  protected fun sendResource(resourceName: String, request: FullHttpRequest, channel: Channel, extraHeaders: HttpHeaders): Boolean {

    val requestedResource = HelpRequestHandlerBase::class.java.getResourceAsStream(
      (if (request.uri().contains("/img/")) "/images/" else "/topics/") + resourceName)

    if (requestedResource != null) {
      val content = IOUtils.toByteArray(requestedResource)
      return sendData(content, resourceName, request, channel, extraHeaders)
    }

    throw Exception("$resourceName not found")
  }
}
