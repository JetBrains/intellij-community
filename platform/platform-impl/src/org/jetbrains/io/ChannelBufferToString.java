package org.jetbrains.io;

import com.intellij.util.text.StringFactory;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

public final class ChannelBufferToString {
  public static String readString(ByteBuf buffer) {
    return charBufferToString(readIntoCharBuffer(null, buffer, buffer.readableBytes()));
  }

  public static String readString(ByteBuf buffer, int byteCount) {
    return charBufferToString(readIntoCharBuffer(null, buffer, byteCount));
  }

  public static String charBufferToString(CharBuffer charBuffer) {
    char[] array = charBuffer.array();
    if (array.length == charBuffer.position()) {
      return StringFactory.createShared(array);
    }
    else {
      return charBuffer.flip().toString();
    }
  }

  public static CharBuffer readIntoCharBuffer(@Nullable CharBuffer charBuffer, @NotNull ByteBuf buffer, int byteCount) {
    CharsetDecoder decoder = CharsetUtil.getDecoder(CharsetUtil.UTF_8);
    ByteBuffer in = buffer.nioBuffer(buffer.readerIndex(), byteCount);
    if (charBuffer == null) {
      charBuffer = CharBuffer.allocate((int) ((double) in.remaining() * decoder.maxCharsPerByte()));
    }
    try {
      CoderResult cr = decoder.decode(in, charBuffer, true);
      if (!cr.isUnderflow()) {
        cr.throwException();
      }
      cr = decoder.flush(charBuffer);
      if (!cr.isUnderflow()) {
        cr.throwException();
      }
    }
    catch (CharacterCodingException x) {
      throw new IllegalStateException(x);
    }

    buffer.skipBytes(byteCount);
    return charBuffer;
  }

  public static void writeIntAsAscii(int value, @NotNull ByteBuf buffer) {
    String string = Integer.toString(value);
    for (int i = 0; i < string.length(); i++) {
      buffer.writeByte(string.charAt(i));
    }
  }
}