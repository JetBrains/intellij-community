// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.netty.buffer

import com.intellij.util.text.CharArrayCharSequence
import java.io.InputStream
import java.io.Writer

class ByteBufUtf8Writer(private val buffer: ByteBuf) : Writer() {
  fun write(inputStream: InputStream, length: Int) {
    buffer.writeBytes(inputStream, length)
  }

  fun ensureWritable(minWritableBytes: Int) {
    buffer.ensureWritable(minWritableBytes)
  }

  override fun write(chars: CharArray, off: Int, len: Int) {
    ByteBufUtil.writeUtf8(buffer, CharArrayCharSequence(chars, off, off + len))
  }

  override fun write(str: String) {
    ByteBufUtil.writeUtf8(buffer, str)
  }

  override fun write(str: String, off: Int, len: Int) {
    ByteBufUtilEx.writeUtf8(buffer, str, off, off + len)
  }

  override fun append(csq: CharSequence?): Writer {
    if (csq == null) {
      ByteBufUtil.writeAscii(buffer, "null")
    }
    else {
      ByteBufUtil.writeUtf8(buffer, csq)
    }
    return this
  }

  override fun append(csq: CharSequence?, start: Int, end: Int): Writer {
    ByteBufUtilEx.writeUtf8(buffer, csq, start, end)
    return this
  }

  override fun flush() {
  }

  override fun close() {
  }
}
