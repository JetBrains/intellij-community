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

import static io.netty.util.internal.StringUtil.isSurrogate;

// todo pull request
public class ByteBufUtilEx {
  private static final byte WRITE_UTF_UNKNOWN = (byte) '?';

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
    for (int i = start; i < end; i++) {
      char c = seq.charAt(i);
      if (c < 0x80) {
        buffer._setByte(writerIndex++, (byte)c);
      }
      else if (c < 0x800) {
        buffer._setByte(writerIndex++, (byte)(0xc0 | (c >> 6)));
        buffer._setByte(writerIndex++, (byte)(0x80 | (c & 0x3f)));
      }
      else if (isSurrogate(c)) {
        if (!Character.isHighSurrogate(c)) {
          buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
          continue;
        }
        final char c2;
        try {
          // Surrogate Pair consumes 2 characters. Optimistically try to get the next character to avoid
          // duplicate bounds checking with charAt. If an IndexOutOfBoundsException is thrown we will
          // re-throw a more informative exception describing the problem.
          //noinspection AssignmentToForLoopParameter
          c2 = seq.charAt(++i);
        }
        catch (IndexOutOfBoundsException e) {
          buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
          break;
        }
        if (!Character.isLowSurrogate(c2)) {
          buffer._setByte(writerIndex++, WRITE_UTF_UNKNOWN);
          buffer._setByte(writerIndex++, Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2);
          continue;
        }
        int codePoint = Character.toCodePoint(c, c2);
        // See http://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
        buffer._setByte(writerIndex++, (byte)(0xf0 | (codePoint >> 18)));
        buffer._setByte(writerIndex++, (byte)(0x80 | ((codePoint >> 12) & 0x3f)));
        buffer._setByte(writerIndex++, (byte)(0x80 | ((codePoint >> 6) & 0x3f)));
        buffer._setByte(writerIndex++, (byte)(0x80 | (codePoint & 0x3f)));
      }
      else {
        buffer._setByte(writerIndex++, (byte)(0xe0 | (c >> 12)));
        buffer._setByte(writerIndex++, (byte)(0x80 | ((c >> 6) & 0x3f)));
        buffer._setByte(writerIndex++, (byte)(0x80 | (c & 0x3f)));
      }
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
}
