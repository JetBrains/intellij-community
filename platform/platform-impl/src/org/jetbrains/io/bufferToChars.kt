// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.util.CharsetUtil
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets

fun ByteBuf.readIntoCharBuffer(byteCount: Int = readableBytes(), charBuffer: CharBuffer) {
  val decoder = CharsetUtil.decoder(StandardCharsets.UTF_8)
  if (nioBufferCount() == 1) {
    decodeString(decoder, internalNioBuffer(readerIndex(), byteCount), charBuffer)
  }
  else {
    val buffer = alloc().heapBuffer(byteCount)
    try {
      buffer.writeBytes(this, readerIndex(), byteCount)
      decodeString(decoder, buffer.internalNioBuffer(0, byteCount), charBuffer)
    }
    finally {
      buffer.release()
    }
  }
}

private fun decodeString(decoder: CharsetDecoder, src: ByteBuffer, dst: CharBuffer) {
  try {
    var cr = decoder.decode(src, dst, true)
    if (!cr.isUnderflow) {
      cr.throwException()
    }
    cr = decoder.flush(dst)
    if (!cr.isUnderflow) {
      cr.throwException()
    }
  }
  catch (x: CharacterCodingException) {
    throw IllegalStateException(x)
  }

}

fun writeIntAsAscii(value: Int, buffer: ByteBuf) {
  ByteBufUtil.writeAscii(buffer, StringBuilder().append(value))
}