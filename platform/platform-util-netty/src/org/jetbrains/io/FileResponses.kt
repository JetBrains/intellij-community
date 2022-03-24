// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PathUtilRt
import com.intellij.util.containers.CollectionFactory
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.DefaultFileRegion
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedNioFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

fun flushChunkedResponse(channel: Channel, isKeepAlive: Boolean) {
  val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
  if (!isKeepAlive) {
    future.addListener(ChannelFutureListener.CLOSE)
  }
}

private val fileExtToMimeType by lazy {
  val map = CollectionFactory.createSmallMemoryFootprintMap<String, String>(1100)
  FileResponses.javaClass.getResourceAsStream("/mime-types.csv").bufferedReader().useLines {//NON-NLS
    for (line in it) {
      if (line.isBlank()) {
        continue
      }

      val commaIndex = line.indexOf(',')
      // don't check negative commaIndex - resource expected to contain only valid data as it is not user supplied
      map.put(line.substring(0, commaIndex), line.substring(commaIndex + 1))
    }
  }
  map
}

object FileResponses {
  fun getContentType(path: String): String {
    return PathUtilRt.getFileExtension(path)?.let { fileExtToMimeType.get(it) } ?: "application/octet-stream"
  }

  @JvmOverloads
  fun checkCache(request: HttpRequest, channel: Channel, lastModified: Long, extraHeaders: HttpHeaders? = null): Boolean {
    val ifModified = request.headers().getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE)
    if (ifModified != null && ifModified >= lastModified) {
      HttpResponseStatus.NOT_MODIFIED.send(channel, request, extraHeaders = extraHeaders)
      return true
    }
    return false
  }

  @JvmOverloads
  fun prepareSend(request: HttpRequest, channel: Channel, lastModified: Long, filename: String, extraHeaders: HttpHeaders? = null): HttpResponse? {
    if (request.headers().get(HttpHeaderNames.RANGE) == null && checkCache(request, channel, lastModified, extraHeaders)) {
      return null
    }
    return doPrepareResponse(DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), filename, lastModified, extraHeaders)
  }

  private fun doPrepareResponse(response: DefaultHttpResponse, filename: String, lastModified: Long, extraHeaders: HttpHeaders?): DefaultHttpResponse {
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType(filename))
    response.addCommonHeaders()
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
    if (response.status() != HttpResponseStatus.PARTIAL_CONTENT) {
      response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(lastModified))
    }
    if (extraHeaders != null) {
      response.headers().add(extraHeaders)
    }
    return response
  }

  fun sendFile(request: HttpRequest, channel: Channel, file: Path, extraHeaders: HttpHeaders? = null) {
    return sendFile(request, channel, file, extraHeaders, null)
  }

  fun sendFile(request: HttpRequest, channel: Channel, file: Path, extraHeaders: HttpHeaders? = null, extraSuffix: ByteArray? = null) {
    val fileChannel: FileChannel
    val rangeHeader = request.headers().get(HttpHeaderNames.RANGE)
    val lastModified: Long
    try {
      lastModified = Files.getLastModifiedTime(file).toMillis()
      if (rangeHeader == null &&
          (extraSuffix == null || extraSuffix.isEmpty()) &&
          checkCache(request, channel, lastModified, extraHeaders)) {
        return
      }

      fileChannel = FileChannel.open(file, StandardOpenOption.READ)
    }
    catch (ignored: NoSuchFileException) {
      HttpResponseStatus.NOT_FOUND.send(channel, request)
      return
    }

    val isKeepAlive: Boolean
    var fileWillBeClosed = false
    try {
      val fileSize = fileChannel.size()
      val responseLength = fileSize + (extraSuffix?.size ?: 0)
      val range = parseRange(rangeHeader, responseLength) ?: ByteRange(0, responseLength)

      val isPartialContent = !(range.start == 0L && range.end == responseLength)
      val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, if (isPartialContent) HttpResponseStatus.PARTIAL_CONTENT else HttpResponseStatus.OK)
      isKeepAlive = response.addKeepAliveIfNeeded(request)
      doPrepareResponse(response, file.fileName.toString(), lastModified, extraHeaders)

      if (isPartialContent) {
        response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes ${range.start}-${range.end - 1}/${responseLength}")//NON-NLS
      }

      HttpUtil.setContentLength(response, range.length)
      channel.write(response)
      if (request.method() !== HttpMethod.HEAD) {
        val fileRange = range.intersect(0, fileSize)
        if (fileRange != null && fileRange.length > 0) {
          if (channel.pipeline().get(SslHandler::class.java) == null) {
            // no encryption - use zero-copy
            channel.write(DefaultFileRegion(fileChannel, range.start, fileRange.length))
          }
          else {
            // cannot use zero-copy with HTTPS
            channel.write(ChunkedNioFile(fileChannel, range.start, fileRange.length, 8192))
          }
        }
        if (extraSuffix != null) {
          val suffixRange = range.intersect(fileSize, fileSize + extraSuffix.size)
          if (suffixRange != null && suffixRange.length > 0) {
            val byteBuf = Unpooled.copiedBuffer(
              extraSuffix, (suffixRange.start - fileSize).toInt(), suffixRange.length.toInt())
            channel.write(byteBuf)
          }
        }
      }
      fileWillBeClosed = true
    }
    finally {
      if (!fileWillBeClosed) {
        fileChannel.close()
      }
    }

    flushChunkedResponse(channel, isKeepAlive)
  }
}

private val RANGE_HEADER = Pattern.compile("bytes=(\\d+)?-(\\d+)?")

// http range end is inclusive, but we use more convenient agreement - end here is exclusive
private data class ByteRange(val start: Long, val end: Long) {
  fun intersect(otherStart: Long, otherEnd: Long): ByteRange? {
    if (start <= otherStart && otherEnd <= end) return ByteRange(otherStart, otherEnd)
    if (otherStart <= start && end <= otherEnd) return this
    if (end <= otherStart || otherEnd <= start) return null
    return ByteRange(max(start, otherStart), min(end, otherEnd))
  }
  val length: Long
    get() = end - start
}

private fun parseRange(header: String?, size: Long): ByteRange? {
  if (header.isNullOrEmpty()) {
    return null
  }

  val m = RANGE_HEADER.matcher(header)
  if (!m.matches()) {
    logger<FileResponses>().error("Range header is invalid: ${header}")
    return null
  }

  if (m.group(1).isNullOrEmpty()) {
    return ByteRange(size - m.group(2).toLong(), size)
  }

  val start = m.group(1).toLong()
  val end: Long
  if (m.group(2).isNullOrEmpty()) {
    end = size - 1
  }
  else {
    end = m.group(2).toLong()
  }

  return when {
    end < start -> {
      logger<FileResponses>().error("start ($start) must be greater than end ($end)")
      null
    }
    end >= size -> {
      logger<FileResponses>().error("end ($end) must be lesser than size ($size)")
      null
    }
    else -> ByteRange(start, end + 1)
  }
}