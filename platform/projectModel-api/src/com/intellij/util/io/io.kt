// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.util.SmartList
import com.intellij.util.text.CharArrayCharSequence
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*

fun InputStreamReader.readCharSequence(length: Int): CharSequence {
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
fun InputStreamReader.readCharSequence(): CharSequence {
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
        buffers = SmartList<CharArray>()
      }
      buffers.add(chars)
      val newLength = Math.min(1024 * 1024, chars.size * 2)
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

// we must return string on subSequence() - JsonReaderEx will call toString in any case
class CharSequenceBackedByChars : CharArrayCharSequence {
  val byteBuffer: ByteBuffer
    get() = Charsets.UTF_8.encode(CharBuffer.wrap(myChars, myStart, length))

  constructor(charBuffer: CharBuffer) : super(charBuffer.array(), charBuffer.arrayOffset(), charBuffer.position())

  constructor(chars: CharArray, start: Int, end: Int) : super(chars, start, end)

  constructor(chars: CharArray) : super(*chars)

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    return if (startIndex == 0 && endIndex == length) this else String(myChars, myStart + startIndex, endIndex - startIndex)
  }
}

fun ByteBuffer.toByteArray(): ByteArray {
  if (hasArray()) {
    val offset = arrayOffset()
    if (offset == 0 && array().size == limit()) {
      return array()
    }
    return Arrays.copyOfRange(array(), offset, offset + limit())
  }

  val bytes = ByteArray(limit())
  get(bytes)
  return bytes
}

fun String.encodeUrlQueryParameter(): String = URLEncoder.encode(this, Charsets.UTF_8.name())!!

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)