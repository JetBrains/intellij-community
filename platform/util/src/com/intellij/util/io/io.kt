// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.util.text.CharSequenceBackedByChars
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.min

fun Reader.readCharSequence(length: Int): CharSequence {
  use {
    val chars = CharArray(length)
    var count = 0
    while (count < chars.size) {
      val n = read(chars, count, chars.size - count)
      if (n <= 0) {
        break
      }
      count += n
    }
    return CharSequenceBackedByChars(chars, 0, count)
  }
}

fun ByteBuffer.toByteArray(isClear: Boolean = false): ByteArray {
  if (hasArray()) {
    val offset = arrayOffset()
    val array = array()
    if (offset == 0 && array.size == limit()) {
      return array
    }

    val result = array.copyOfRange(offset, offset + limit())
    if (isClear) {
      array.fill(0)
    }
    return result
  }

  val bytes = ByteArray(limit() - position())
  get(bytes)
  return bytes
}

@Deprecated("Use URLEncoder.encode()")
@Suppress("DeprecatedCallableAddReplaceWith", "NOTHING_TO_INLINE")
inline fun String.encodeUrlQueryParameter(): String = URLEncoder.encode(this, Charsets.UTF_8.name())!!

@Deprecated("Use java.util.Base64.getDecoder().decode()")
@Suppress("DeprecatedCallableAddReplaceWith", "NOTHING_TO_INLINE")
inline fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

/**
 * Behaves like [InputStream.copyTo], but doesn't block _current_ coroutine context even for a second.
 * Due to unavailability of non-blocking IO for [InputStream], all blocking calls are executed on some daemonic thread, and some I/O
 * operations may outlive current coroutine context.
 *
 * It's safe to set [java.net.Socket.setSoTimeout] if [InputStream] comes from a socket.
 */
@ApiStatus.Experimental
@OptIn(DelicateCoroutinesApi::class)
suspend fun InputStream.copyToAsync(
  outputStream: OutputStream,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
  limit: Long = Long.MAX_VALUE,
) {
  computeDetached(context = CoroutineName("copyToAsync: $this => $outputStream")) {
    val buffer = ByteArray(bufferSize)
    var totalRead = 0L
    while (totalRead < limit) {
      yield()
      val read =
        try {
          read(buffer, 0, min(limit - totalRead, buffer.size.toLong()).toInt())
        }
        catch (_: SocketTimeoutException) {
          continue
        }
      when {
        read < 0 -> break
        read > 0 -> {
          totalRead += read
          yield()
          // According to Javadoc, Socket.soTimeout doesn't have any influence on SocketOutputStream.
          // Had timeout affected sends, it would have impossible to distinguish if the packets were delivered or not in case of timeout.
          outputStream.write(buffer, 0, read)
        }
        else -> Unit
      }
    }
  }
}