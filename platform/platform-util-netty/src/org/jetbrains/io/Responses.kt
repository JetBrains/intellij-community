// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Responses")
package org.jetbrains.io

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*

private var SERVER_HEADER_VALUE: String? = null

fun response(contentType: String?, content: ByteBuf?): FullHttpResponse {
  val response = if (content == null)
    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
  else
    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
  if (contentType != null) {
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
  }
  return response
}

fun response(content: CharSequence, charset: Charset = CharsetUtil.US_ASCII): FullHttpResponse {
  return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(content, charset))
}

fun HttpResponse.addNoCache(): HttpResponse {
  headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate, max-age=0")//NON-NLS
  headers().add(HttpHeaderNames.PRAGMA, "no-cache")//NON-NLS
  return this
}

val serverHeaderValue: String?
  get() {
    if (SERVER_HEADER_VALUE == null) {
      val app = ApplicationManager.getApplication()
      if (app != null && !app.isDisposed) {
        SERVER_HEADER_VALUE = ApplicationInfo.getInstance().fullApplicationName
      }
    }
    return SERVER_HEADER_VALUE
  }

fun HttpResponse.addServer() {
  serverHeaderValue?.let {
    headers().add(HttpHeaderNames.SERVER, it)
  }
}

@JvmOverloads
fun HttpResponse.send(channel: Channel, request: HttpRequest?, extraHeaders: HttpHeaders? = null) {
  if (status() != HttpResponseStatus.NOT_MODIFIED && !HttpUtil.isContentLengthSet(this)) {
    HttpUtil.setContentLength(this, (if (this is FullHttpResponse) content().readableBytes() else 0).toLong())
  }

  addCommonHeaders()
  extraHeaders?.let {
    headers().add(it)
  }
  send(channel, request != null && !addKeepAliveIfNeeded(request))
}

fun HttpResponse.addKeepAliveIfNeeded(request: HttpRequest): Boolean {
  if (HttpUtil.isKeepAlive(request)) {
    HttpUtil.setKeepAlive(this, true)
    return true
  }
  return false
}

fun HttpResponse.addCommonHeaders() {
  addServer()
  if (!headers().contains(HttpHeaderNames.DATE)) {
    headers().set(HttpHeaderNames.DATE, Calendar.getInstance().time)
  }

  if (!headers().contains(HttpHeaderNames.X_FRAME_OPTIONS)) {
    headers().set(HttpHeaderNames.X_FRAME_OPTIONS, "SameOrigin")
  }
  @Suppress("SpellCheckingInspection")
  headers().set("X-Content-Type-Options", "nosniff")//NON-NLS
  headers().set("x-xss-protection", "1; mode=block")//NON-NLS

  if (status() < HttpResponseStatus.MULTIPLE_CHOICES) {
    headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes")//NON-NLS
  }
}

fun HttpResponse.send(channel: Channel, close: Boolean) {
  if (!channel.isActive) {
    return
  }

  val future = channel.write(this)
  if (this !is FullHttpResponse) {
    channel.write(LastHttpContent.EMPTY_LAST_CONTENT)
  }
  channel.flush()
  if (close) {
    future.addListener(ChannelFutureListener.CLOSE)
  }
}

fun HttpResponseStatus.response(request: HttpRequest? = null, description: String? = null): HttpResponse = createStatusResponse(this, request, description)

@JvmOverloads
fun HttpResponseStatus.send(channel: Channel, request: HttpRequest? = null, description: String? = null, extraHeaders: HttpHeaders? = null) {
  createStatusResponse(this, request, description).send(channel, request, extraHeaders)
}

@JvmOverloads
fun HttpResponseStatus.sendPlainText(channel: Channel, request: HttpRequest? = null, description: String? = null, extraHeaders: HttpHeaders? = null) {
  createStatusResponse(this, request, description, usePlainText = true).send(channel, request, extraHeaders)
}

internal fun createStatusResponse(responseStatus: HttpResponseStatus,
                                  request: HttpRequest?,
                                  description: String? = null,
                                  usePlainText: Boolean = false): HttpResponse {
  if (request != null && request.method() == HttpMethod.HEAD) {
    return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, Unpooled.EMPTY_BUFFER)
  }

  val message = responseStatus.toString()

  @NlsSafe
  val builder = StringBuilder()
  if (usePlainText) {
    builder.append(message)
    if (description != null) {
      builder.append("\n").append(description)
    }
  }
  else {
    builder.append("<!doctype html><title>").append(message).append("</title>")
      .append("<h1 style=\"text-align: center\">").append(message).append("</h1>")
    if (description != null) {
      builder.append("<p>").append(description).append("</p>")
    }
  }

  val content = ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(builder), Charsets.UTF_8)
  val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, content)
  response.headers().set(HttpHeaderNames.CONTENT_TYPE, if (usePlainText) HttpHeaderValues.TEXT_PLAIN else HttpHeaderValues.TEXT_HTML)
  return response
}