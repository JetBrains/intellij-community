package org.jetbrains.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.CharBuffer;

public abstract class MessageDecoder extends Decoder {
  protected int contentLength;
  protected final StringBuilder builder = new StringBuilder(64);

  private CharBuffer chunkedContent;
  private int consumedContentByteCount = 0;

  protected final int parseContentLength() {
    return parseInt(builder, 0, false, 10);
  }

  @Nullable
  protected final CharSequence readChars(@NotNull ByteBuf input) throws IOException {
    int readableBytes = input.readableBytes();
    if (readableBytes == 0) {
      input.release();
      return null;
    }

    int required = contentLength - consumedContentByteCount;
    if (readableBytes < required) {
      if (chunkedContent == null) {
        chunkedContent = CharBuffer.allocate(contentLength);
      }

      ChannelBufferToString.readIntoCharBuffer(input, readableBytes, chunkedContent);
      consumedContentByteCount += readableBytes;
      input.release();
      return null;
    }
    else {
      CharBuffer charBuffer = chunkedContent;
      if (charBuffer != null) {
        chunkedContent = null;
        consumedContentByteCount = 0;
      }
      return new JsonReaderEx.CharSequenceBackedByChars(ChannelBufferToString.readIntoCharBuffer(input, required, charBuffer));
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    try {
      chunkedContent = null;
    }
    finally {
      super.channelInactive(context);
    }
  }

  public static boolean readUntil(char what, @NotNull ByteBuf buffer, @NotNull StringBuilder builder) {
    int i = buffer.readerIndex();
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int n = buffer.writerIndex(); i < n; i++) {
      char c = (char)buffer.getByte(i);
      if (c == what) {
        buffer.readerIndex(i + 1);
        return true;
      }
      else {
        builder.append(c);
      }
    }
    buffer.readerIndex(i);
    return false;
  }

  public static void skipWhitespace(@NotNull ByteBuf buffer) {
    int i = buffer.readerIndex();
    int n = buffer.writerIndex();
    for (; i < n; i++) {
      char c = (char)buffer.getByte(i);
      if (c != ' ') {
        buffer.readerIndex(i);
        return;
      }
    }
    buffer.readerIndex(n);
  }

  /**
   * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
   * Copyright (C) 2006 - Javolution (http://javolution.org/)
   * All rights reserved.
   *
   * Permission to use, copy, modify, and distribute this software is
   * freely granted, provided that this notice is preserved.
   */
  public static int parseInt(@NotNull CharSequence value, int start, boolean isNegative, int radix) {
    final int end = value.length();
    int result = 0; // Accumulates negatively (avoid MIN_VALUE overflow).
    int i = start;
    for (; i < end; i++) {
      char c = value.charAt(i);
      int digit = (c <= '9') ? c - '0'
                             : ((c <= 'Z') && (c >= 'A')) ? c - 'A' + 10
                                                          : ((c <= 'z') && (c >= 'a')) ? c - 'a' + 10 : -1;
      if ((digit >= 0) && (digit < radix)) {
        int newResult = result * radix - digit;
        if (newResult > result) {
          throw new NumberFormatException("Overflow parsing " + value.subSequence(start, end));
        }
        result = newResult;
      }
      else {
        break;
      }
    }
    // Requires one valid digit character and checks for opposite overflow.
    if ((result == 0) && ((end == 0) || (value.charAt(i - 1) != '0'))) {
      throw new NumberFormatException("Invalid integer representation for " + value.subSequence(start, end));
    }
    if ((result == Integer.MIN_VALUE) && !isNegative) {
      throw new NumberFormatException("Overflow parsing " + value.subSequence(start, end));
    }
    return isNegative ? result : -result;
  }
}