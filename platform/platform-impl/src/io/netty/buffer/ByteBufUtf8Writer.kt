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
package io.netty.buffer

import com.intellij.util.io.writeUtf8
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
    buffer.writeUtf8(CharArrayCharSequence(chars, off, off + len))
  }

  override fun write(str: String) {
    buffer.writeUtf8(str)
  }

  override fun write(str: String, off: Int, len: Int) {
    ByteBufUtilEx.writeUtf8(buffer, str, off, off + len)
  }

  override fun append(csq: CharSequence?): Writer {
    if (csq == null) {
      ByteBufUtil.writeAscii(buffer, "null")
    }
    else {
      buffer.writeUtf8(csq)
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
