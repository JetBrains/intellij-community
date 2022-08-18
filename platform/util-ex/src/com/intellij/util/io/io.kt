// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.util.SmartList
import com.intellij.util.text.CharSequenceBackedByChars
import java.io.Reader
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

/**
 * Think twice before use - consider to to specify length.
 */
fun Reader.readCharSequence(): CharSequence {
  var chars = CharArray(DEFAULT_BUFFER_SIZE)
  var buffers: MutableList<CharArray>? = null
  var count = 0
  var total = 0
  while (true) {
    val n = read(chars, count, chars.size - count)
    if (n <= 0) {
      break
    }

    count += n
    total += n
    if (count == chars.size) {
      if (buffers == null) {
        buffers = SmartList()
      }
      buffers.add(chars)
      val newLength = min(1024 * 1024, chars.size * 2)
      chars = CharArray(newLength)
      count = 0
    }
  }

  if (buffers == null) {
    return CharSequenceBackedByChars(chars, 0, total)
  }

  val result = CharArray(total)
  for (buffer in buffers) {
    System.arraycopy(buffer, 0, result, result.size - total, buffer.size)
    total -= buffer.size
  }
  System.arraycopy(chars, 0, result, result.size - total, total)
  return CharSequenceBackedByChars(result)
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

fun String.encodeUrlQueryParameter(): String = URLEncoder.encode(this, Charsets.UTF_8.name())!!

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)