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
package com.intellij.lang.ant.segments;

import com.intellij.rt.ant.execution.Packet;
import com.intellij.rt.ant.execution.PacketProcessor;
import com.intellij.rt.ant.execution.SegmentedStream;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class SegmentedInputStream extends InputStream {
  private final PushReader mySourceStream;
  private PacketProcessor myEventsDispatcher;
  private int myStartupPassed = 0;

  public SegmentedInputStream(@NotNull InputStream sourceStream, @NotNull Charset charset) {
    mySourceStream = new PushReader(new BufferedReader(new InputStreamReader(sourceStream, charset)));
  }

  @Override
  public int read() throws IOException {
    if (myStartupPassed < SegmentedStream.STARTUP_MESSAGE.length()) {
      return rawRead();
    } else {
      return findNextSymbol();
    }
  }

  private int rawRead() throws IOException {
    while(myStartupPassed < SegmentedStream.STARTUP_MESSAGE.length()) {
      final int aChar = readNext();
      if (aChar != SegmentedStream.STARTUP_MESSAGE.charAt(myStartupPassed)) {
        mySourceStream.pushBack(aChar);
        mySourceStream.pushBack(SegmentedStream.STARTUP_MESSAGE.substring(0, myStartupPassed).toCharArray());
        myStartupPassed = 0;
        return readNext();
      }
      myStartupPassed++;
    }
    return read();
  }

  private int findNextSymbol() throws IOException {
    int nextByte;
    while (true) {
      nextByte = readNext();
      if (nextByte != SegmentedStream.SPECIAL_SYMBOL) break;
      final Integer packetRead = readControlSequence();
      if (packetRead != null) break;
      if (available() == 0) return -1;
    }
    return nextByte;
  }

  private Integer readControlSequence() throws IOException {
    for (int idx = 1; idx < SegmentedStream.MARKER_PREFIX.length(); idx++) {
      final int readAhead = readNext();
      if (readAhead != SegmentedStream.MARKER_PREFIX.charAt(idx)) {
        return readAhead;
      }
    }
    final char[] marker = readMarker();
    if(myEventsDispatcher != null) myEventsDispatcher.processPacket(decode(marker));
    return null;
  }

  public void setEventsDispatcher(final PacketProcessor eventsDispatcher) {
    myEventsDispatcher = eventsDispatcher;
  }

  private char[] readMarker() throws IOException {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      int nextRead = '0';
      while (nextRead != ' ' && nextRead != SegmentedStream.SPECIAL_SYMBOL) {
        buffer.append((char)nextRead);
        nextRead = readNext();
        if (nextRead == -1) return new char[0];
      }
      return readNext(Integer.valueOf(buffer.toString()).intValue());
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private char[] readNext(final int charCount) throws IOException {
    return mySourceStream.next(charCount);
  }

  private int readNext() throws IOException {
    return mySourceStream.next();
  }

  @Override
  public int available() throws IOException {
    return mySourceStream.ready() ? 1 : 0;
  }

  @Override
  public void close() throws IOException {
    mySourceStream.close();
  }

  public static String decode(final char[] chars) {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      for (int i = 0; i < chars.length; i++) {
        char chr = chars[i];
        final char decodedChar;
        if (chr == Packet.ourSpecialSymbol) {
          i++;
          chr = chars[i];
          if (chr != Packet.ourSpecialSymbol) {
            final StringBuilder codeBuffer = new StringBuilder(Packet.CODE_LENGTH);
            codeBuffer.append(chr);
            for (int j = 1; j < Packet.CODE_LENGTH; j++) {
              codeBuffer.append(chars[i + j]);
            }
            i += Packet.CODE_LENGTH - 1;
            decodedChar = (char)Integer.parseInt(codeBuffer.toString());
          }
          else decodedChar = chr;
        } else decodedChar = chr;
        buffer.append(decodedChar);
      }
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }
}
