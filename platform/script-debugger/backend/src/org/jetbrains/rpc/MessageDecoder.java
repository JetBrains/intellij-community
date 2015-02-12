package org.jetbrains.rpc;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.ChannelBufferToString;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;

import java.nio.CharBuffer;

public abstract class MessageDecoder extends SimpleChannelInboundHandlerAdapter<ByteBuf> {
  protected int contentLength;
  protected final StringBuilder builder = new StringBuilder(64);

  private CharBuffer chunkedContent;
  private int consumedContentByteCount = 0;

  protected final int parseContentLength() {
    return parseInt(builder, 0, false, 10);
  }

  @Nullable
  protected String doReadContent(@NotNull ByteBuf buffer) {
    int required = contentLength - consumedContentByteCount;
    String result;
    if (buffer.readableBytes() < required) {
      if (chunkedContent == null) {
        chunkedContent = CharBuffer.allocate(contentLength);
      }

      int count = buffer.readableBytes();
      ChannelBufferToString.readIntoCharBuffer(chunkedContent, buffer, count);
      consumedContentByteCount += count;
      return null;
    }
    else if (chunkedContent != null) {
      ChannelBufferToString.readIntoCharBuffer(chunkedContent, buffer, required);
      result = ChannelBufferToString.charBufferToString(chunkedContent);

      chunkedContent = null;
      consumedContentByteCount = 0;
      return result;
    }
    else {
      // we can produce char sequence CharSequence result = CharsetUtil.UTF_8.decode(buffer.toByteBuffer(buffer.readerIndex(), required));
      // but later, in JsonReaderEx, it will be toString in any case, so, in this case, intermediate java.nio.HeapCharBuffer will be created - so, we stay with String
      return ChannelBufferToString.readString(buffer, required);
    }
  }

  /**
   * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
   * Copyright (C) 2006 - Javolution (http://javolution.org/)
   * All rights reserved.
   *
   * Permission to use, copy, modify, and distribute this software is
   * freely granted, provided that this notice is preserved.
   */
  private static int parseInt(final CharSequence value, final int start, final boolean isNegative, final int radix) {
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