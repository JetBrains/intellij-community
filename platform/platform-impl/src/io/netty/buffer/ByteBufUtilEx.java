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
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;

// todo pull request
public class ByteBufUtilEx {
  public static int writeUtf8(ByteBuf buf, CharSequence seq) {
    return writeUtf8(buf, seq, 0, seq.length());
  }

  public static int writeUtf8(ByteBuf buf, CharSequence seq, int start, int end) {
    if (buf == null) {
      throw new NullPointerException("buf");
    }
    if (seq == null) {
      throw new NullPointerException("seq");
    }
    // UTF-8 uses max. 3 bytes per char, so calculate the worst case.
    final int len = end - start;
    final int maxSize = len * 3;
    buf.ensureWritable(maxSize);

    int oldWriterIndex;
    AbstractByteBuf buffer;
    if (buf instanceof AbstractByteBuf) {
      buffer = (AbstractByteBuf)buf;
      oldWriterIndex = buffer.writerIndex;

    }
    else {
      ByteBuf underlying = buf.unwrap();
      if (underlying instanceof AbstractByteBuf) {
        buffer = (AbstractByteBuf)underlying;
        oldWriterIndex = buf.writerIndex();
      }
      else {
        byte[] bytes = seq.toString().getBytes(CharsetUtil.UTF_8);
        buf.writeBytes(bytes);
        return bytes.length;
      }
    }

    int writerIndex = oldWriterIndex;
    // We can use the _set methods as these not need to do any index checks and reference checks.
    // This is possible as we called ensureWritable(...) before.
    for (int i = start; i < end; i++) {
      writerIndex = writeChar(buffer, writerIndex, seq.charAt(i));
    }

    // update the writerIndex without any extra checks for performance reasons
    if (buf == buffer) {
      buffer.writerIndex = writerIndex;
    }
    else {
      buf.writerIndex(writerIndex);
    }
    return writerIndex - oldWriterIndex;
  }

  static int writeChar(AbstractByteBuf buffer, int writerIndex, int c) {
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
    return writerIndex;
  }

  @NotNull
  static AbstractByteBuf getBuf(@NotNull ByteBuf buffer) {
    if (buffer instanceof AbstractByteBuf) {
      return (AbstractByteBuf)buffer;
    }
    else {
      return (AbstractByteBuf)((WrappedByteBuf)buffer).buf;
    }
  }
}
