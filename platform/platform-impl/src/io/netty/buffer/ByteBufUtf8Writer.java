/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package io.netty.buffer;

import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public final class ByteBufUtf8Writer extends Writer {
  private final ByteBuf buffer;

  public ByteBufUtf8Writer(@NotNull ByteBuf buffer) {
    this.buffer = buffer;
  }

  public void write(@NotNull InputStream inputStream, int length) throws IOException {
    buffer.writeBytes(inputStream, length);
  }

  @Override
  public void write(int c) {
    AbstractByteBuf buffer = ByteBufUtilEx.getBuf(this.buffer);
    int writerIndex = this.buffer.writerIndex();
    if (c < 0x80) {
      buffer._setByte(writerIndex++, (byte)c);
    }
    else if (c < 0x800) {
      buffer._setByte(writerIndex++, (byte)(0xc0 | (c >> 6)));
      buffer._setByte(writerIndex++, (byte)(0x80 | (c & 0x3f)));
    }
    else {
      buffer._setByte(writerIndex++, (byte)(0xe0 | (c >> 12)));
      buffer._setByte(writerIndex++, (byte)(0x80 | ((c >> 6) & 0x3f)));
      buffer._setByte(writerIndex++, (byte)(0x80 | (c & 0x3f)));
    }
    this.buffer.writerIndex(writerIndex);
  }

  @Override
  public void write(char[] chars, int off, int len) {
    ByteBufUtilEx.writeUtf8(buffer, new CharArrayCharSequence(chars, off, off + len));
  }

  @Override
  public void write(String str) {
    ByteBufUtilEx.writeUtf8(buffer, str);
  }

  @Override
  public void write(String str, int off, int len) {
    ByteBufUtilEx.writeUtf8(buffer, str, off, off + len);
  }

  @Override
  public Writer append(CharSequence csq) {
    if (csq == null) {
      ByteBufUtil.writeAscii(buffer, "null");
    }
    else {
      ByteBufUtilEx.writeUtf8(buffer, csq);
    }
    return this;
  }

  @Override
  public Writer append(CharSequence csq, int start, int end) {
    ByteBufUtilEx.writeUtf8(buffer, csq, start, end);
    return this;
  }

  @Override
  public void flush() {
  }

  @Override
  public void close() {
  }
}
