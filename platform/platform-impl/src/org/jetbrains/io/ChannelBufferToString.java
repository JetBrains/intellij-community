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
    return new JsonReaderEx.CharSequenceBackedByChars(readIntoCharBuffer(buffer, buffer.readableBytes(), null));
  }

  @SuppressWarnings("unused")
  @NotNull
  public static CharSequence readChars(@NotNull ByteBuf buffer, int byteCount) throws IOException {
    return new JsonReaderEx.CharSequenceBackedByChars(readIntoCharBuffer(buffer, byteCount, null));
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