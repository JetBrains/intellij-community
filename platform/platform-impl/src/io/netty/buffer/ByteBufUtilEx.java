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

import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.CharBuffer;

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

  @SuppressWarnings("SpellCheckingInspection")
  public static void readUtf8(@NotNull ByteBuf buf, int byteCount, @NotNull CharBuffer charBuffer) throws IOException {
    AbstractByteBuf buffer = getBuf(buf);
    int readerIndex = buf.readerIndex();

    int c, char2, char3;
    int count = 0;

    int byteIndex = readerIndex;
    int charIndex = charBuffer.position();
    char[] chars = charBuffer.array();
    while (count < byteCount) {
      c = buffer._getByte(byteIndex++) & 0xff;
      if (c > 127) {
        break;
      }

      count++;
      chars[charIndex++] = (char)c;
    }

    // byteIndex incremented before check "c > 127", so, we must reset it
    byteIndex = readerIndex + count;
    while (count < byteCount) {
      c = buffer._getByte(byteIndex++) & 0xff;
      switch (c >> 4) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
          // 0xxxxxxx
          count++;
          chars[charIndex++] = (char)c;
          break;

        case 12:
        case 13:
          // 110x xxxx   10xx xxxx
          count += 2;
          if (count > byteCount) {
            throw new UTFDataFormatException("malformed input: partial character at end");
          }
          char2 = (int)buffer._getByte(byteIndex++);
          if ((char2 & 0xC0) != 0x80) {
            throw new UTFDataFormatException("malformed input around byte " + count);
          }
          chars[charIndex++] = (char)(((c & 0x1F) << 6) | (char2 & 0x3F));
          break;

        case 14:
          // 1110 xxxx  10xx xxxx  10xx xxxx
          count += 3;
          if (count > byteCount) {
            throw new UTFDataFormatException("malformed input: partial character at end");
          }
          char2 = buffer._getByte(byteIndex++);
          char3 = buffer._getByte(byteIndex++);
          if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
            throw new UTFDataFormatException("malformed input around byte " + (count - 1));
          }
          chars[charIndex++] = (char)(((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F)));
          break;

        default:
          // 10xx xxxx,  1111 xxxx
          throw new UTFDataFormatException("malformed input around byte " + count);
      }
    }

    if (buf == buffer) {
      buffer.readerIndex = readerIndex + byteCount;
    }
    else {
      buf.readerIndex(readerIndex + byteCount);
    }
    charBuffer.position(charIndex);
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
