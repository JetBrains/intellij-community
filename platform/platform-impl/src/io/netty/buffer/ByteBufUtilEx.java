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

public class ByteBufUtilEx {
  // todo pull request
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
    if (buf instanceof AbstractByteBuf) {
      // Fast-Path
      AbstractByteBuf buffer = (AbstractByteBuf)buf;
      int oldWriterIndex = buffer.writerIndex;
      int writerIndex = oldWriterIndex;

      // We can use the _set methods as these not need to do any index checks and reference checks.
      // This is possible as we called ensureWritable(...) before.
      for (int i = start; i < end; i++) {
        char c = seq.charAt(i);
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
      }
      // update the writerIndex without any extra checks for performance reasons
      buffer.writerIndex = writerIndex;
      return writerIndex - oldWriterIndex;
    }
    else {
      // Maybe we could also check if we can unwrap() to access the wrapped buffer which
      // may be an AbstractByteBuf. But this may be overkill so let us keep it simple for now.
      byte[] bytes = seq.toString().getBytes(CharsetUtil.UTF_8);
      buf.writeBytes(bytes);
      return bytes.length;
    }
  }
}
