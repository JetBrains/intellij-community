package org.jetbrains.io;

import com.intellij.util.text.CharArrayCharSequence;
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
  @NotNull
  public static CharSequence readChars(@NotNull ByteBuf buffer) throws CharacterCodingException {
    return new MyCharArrayCharSequence(readIntoCharBuffer(CharsetUtil.getDecoder(CharsetUtil.UTF_8), buffer, buffer.readableBytes(), null));
  }

  @SuppressWarnings("unused")
  @NotNull
  public static CharSequence readChars(@NotNull ByteBuf buffer, int byteCount) throws CharacterCodingException {
    return new MyCharArrayCharSequence(readIntoCharBuffer(CharsetUtil.getDecoder(CharsetUtil.UTF_8), buffer, byteCount, null));
  }

  @NotNull
  public static CharBuffer readIntoCharBuffer(@NotNull CharsetDecoder decoder, @NotNull ByteBuf buffer, int byteCount, @Nullable CharBuffer charBuffer) throws CharacterCodingException {
    ByteBuffer in = buffer.nioBuffer(buffer.readerIndex(), byteCount);
    if (charBuffer == null) {
      charBuffer = CharBuffer.allocate((int)((float)in.remaining() * decoder.maxCharsPerByte()));
    }

    CoderResult cr = decoder.decode(in, charBuffer, true);
    if (!cr.isUnderflow()) {
      cr.throwException();
    }
    cr = decoder.flush(charBuffer);
    if (!cr.isUnderflow()) {
      cr.throwException();
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

  // we can produce char sequence CharSequence result = CharsetUtil.UTF_8.decode(buffer.nioBuffer(buffer.readerIndex(), required));
  // but later, in JsonReaderEx, it will be toString in any case, so, in this case, intermediate java.nio.HeapCharBuffer will be created - so, we stay with String
  // we must return string on subSequence() - JsonReaderEx will call toString in any case
  public static final class MyCharArrayCharSequence extends CharArrayCharSequence {
    public MyCharArrayCharSequence(@NotNull CharBuffer charBuffer) {
      super(charBuffer.array(), charBuffer.arrayOffset(), charBuffer.position());
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return start == 0 && end == length() ? this : new String(myChars, myStart + start, end - start);
    }
  }
}