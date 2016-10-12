/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.util.CharsetUtil
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder

fun ByteBuf.readIntoCharBuffer(byteCount: Int = readableBytes(), charBuffer: CharBuffer) {
  val decoder = CharsetUtil.decoder(CharsetUtil.UTF_8)
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