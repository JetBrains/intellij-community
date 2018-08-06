// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.DefaultFileRegion
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedFile
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.activation.MimetypesFileTypeMap

private val FILE_MIMETYPE_MAP = MimetypesFileTypeMap()

fun flushChunkedResponse(channel: Channel, isKeepAlive: Boolean) {
  val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
  if (!isKeepAlive) {
    future.addListener(ChannelFutureListener.CLOSE)
  }
}

object FileResponses {
  fun getContentType(path: String): String {
    return FILE_MIMETYPE_MAP.getContentType(path)
  }

  private fun checkCache(request: HttpRequest, channel: Channel, lastModified: Long, extraHeaders: HttpHeaders): Boolean {
    val ifModified = request.headers().getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE)
    if (ifModified != null && ifModified >= lastModified) {
      HttpResponseStatus.NOT_MODIFIED.send(channel, request, extraHeaders = extraHeaders)
      return true
    }
    return false
  }

  fun prepareSend(request: HttpRequest, channel: Channel, lastModified: Long, filename: String, extraHeaders: HttpHeaders): HttpResponse? {
    if (checkCache(request, channel, lastModified, extraHeaders)) {
      return null
    }

    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType(filename))
    response.addCommonHeaders()
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate")
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(lastModified))
    response.headers().add(extraHeaders)
    return response
  }

  fun sendFile(request: HttpRequest, channel: Channel, file: Path, extraHeaders: HttpHeaders = EmptyHttpHeaders.INSTANCE) {
    val response = prepareSend(request, channel, Files.getLastModifiedTime(file).toMillis(), file.fileName.toString(), extraHeaders) ?: return

    val isKeepAlive = response.addKeepAliveIfNeed(request)

    var fileWillBeClosed = false
    val raf: RandomAccessFile
    try {
      raf = RandomAccessFile(file.toFile(), "r")
    }
    catch (ignored: FileNotFoundException) {
      HttpResponseStatus.NOT_FOUND.send(channel, request)
      return
    }

    try {
      val fileLength = raf.length()
      HttpUtil.setContentLength(response, fileLength)

      channel.write(response)
      if (request.method() !== HttpMethod.HEAD) {
        if (channel.pipeline().get(SslHandler::class.java) == null) {
          // no encryption - use zero-copy
          channel.write(DefaultFileRegion(raf.channel, 0, fileLength))
        }
        else {
          // cannot use zero-copy with HTTPS
          channel.write(ChunkedFile(raf))
        }
      }
      fileWillBeClosed = true
    }
    finally {
      if (!fileWillBeClosed) {
        raf.close()
      }
    }

    flushChunkedResponse(channel, isKeepAlive)
  }
}