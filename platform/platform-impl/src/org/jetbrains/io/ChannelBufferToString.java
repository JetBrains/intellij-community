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
package org.jetbrains.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.ByteBufUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.CharBuffer;

public final class ChannelBufferToString {
  @NotNull
  public static CharSequence readChars(@NotNull ByteBuf buffer) throws IOException {
    return new CharSequenceBackedByChars(readIntoCharBuffer(buffer, buffer.readableBytes(), null));
  }

  @SuppressWarnings("unused")
  @NotNull
  public static CharSequence readChars(@NotNull ByteBuf buffer, int byteCount) throws IOException {
    return new CharSequenceBackedByChars(readIntoCharBuffer(buffer, byteCount, null));
  }

  @NotNull
  public static CharBuffer readIntoCharBuffer(@NotNull ByteBuf buffer, int byteCount, @Nullable CharBuffer charBuffer) throws IOException {
    if (charBuffer == null) {
      charBuffer = CharBuffer.allocate(byteCount);
    }
    ByteBufUtilEx.readUtf8(buffer, byteCount, charBuffer);
    return charBuffer;
  }

  public static void writeIntAsAscii(int value, @NotNull ByteBuf buffer) {
    ByteBufUtil.writeAscii(buffer, new StringBuilder().append(value));
  }
}