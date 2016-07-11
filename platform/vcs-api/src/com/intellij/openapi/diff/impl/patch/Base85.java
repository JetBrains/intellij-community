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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterInputStream;
import java.util.zip.Inflater;

public class Base85 {

  static final ArrayList<Character> chars_85 = ContainerUtil.newArrayList(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
    'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
    'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
    'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
    'u', 'v', 'w', 'x', 'y', 'z',
    '!', '#', '$', '%', '&', '(', ')', '*', '+', '-',
    ';', '<', '=', '>', '?', '@', '^', '_', '`', '{',
    '|', '}', '~'
  );

  public static char getCharForLineSize(int lineSize) {
    return getChar(chars_85.indexOf('A') + lineSize - 1);
  }

  public static int getLineSizeFromChar(char charSize) {
    return chars_85.indexOf(charSize) - chars_85.indexOf('A') + 1;
  }

  public static void encode(@NotNull InputStream input, long size, Writer writer) throws IOException {
    int maxLineSize = 52;
    byte[] deflated = new byte[maxLineSize];
    long leftByteSize = size;
    DeflaterInputStream deflaterStream = new DeflaterInputStream(input);
    try {
      do {
        int lineSize = deflaterStream.read(deflated, 0, maxLineSize);
        if (lineSize < 0) break;
        writer.append(getCharForLineSize(lineSize));
        writer.append(encodeBlockLine(deflated, lineSize));
        writer.append('\n');  //not sure that git understands system line separator,
        // see https://github.com/git/git/commit/051308f6e9cebeb76b8fb4f52b7e9e7ce064445c
        leftByteSize -= lineSize;
      }
      while (leftByteSize > 0);
    }
    finally {
      deflaterStream.close();
    }
  }

  private static String encodeBlockLine(byte[] bytes, int lineSize) {
    //max block size can't exceed 65 bytes
    char[] encodedLine = new char[70];
    int remainedLen = lineSize;
    int i = 0;
    int resultOffset = 0;

    while (remainedLen > 0) {
      int byteMask = 0;
      for (int cnt = 24; cnt >= 0; cnt -= 8) {
        int ch = Byte.toUnsignedInt(bytes[i]);
        i++;
        byteMask |= (ch << cnt);
        if (--remainedLen == 0) {
          break;
        }
      }
      long unsignedLong = Integer.toUnsignedLong(byteMask);
      for (int cnt = 4; cnt >= 0; cnt--) {
        long val = unsignedLong % 85;
        unsignedLong /= 85;
        encodedLine[resultOffset + cnt] = getChar((int)val);
      }
      resultOffset += 5;
    }

    int newSize = (lineSize + 3) / 4 * 5;
    return new String(encodedLine, 0, newSize);
  }

  private static char getChar(int i) {
    return chars_85.get(i);
  }

  public static void decode(ListIterator<String> input, long size, ByteArrayOutputStream output) throws IOException, BinaryPatchException {
    Inflater inflater = new Inflater();
    byte[] inflated = new byte[1024];
    try {
      String line = input.next();
      while (line != null && line.length() > 0) {
        char sizeChar = line.charAt(0);
        if (sizeChar < 'A' || sizeChar > 'z') {
          throw new BinaryPatchException("Can't decode binary file patch: wrong data line size");
        }
        int len = getLineSizeFromChar(sizeChar);
        byte[] toInflate = decodeBlockLine(line, len);
        inflater.setInput(toInflate);
        int resultLength;
        try {
          resultLength = inflater.inflate(inflated);
        }
        catch (DataFormatException e) {
          throw new BinaryPatchException("Can't decode binary file patch: can't decompress data");
        }
        output.write(inflated, 0, resultLength);
        if (!input.hasNext()) break;
        line = input.next();
      }
      int count = output.size();
      if (count != size) {
        throw new BinaryPatchException(String.format("Length of decoded binary patch mismatches: expected %d, received %d", size, count));
      }
    }
    finally {
      inflater.end();
    }
  }

  private static byte[] decodeBlockLine(String data, int len) throws BinaryPatchException {
    int leftLen = len;
    int newSize = (data.length() - 1 + 4) / 5 * 4;
    byte[] result = new byte[newSize];
    int resultOffset = 0;
    int dataOffset = 1; // 0 - line size
    while (leftLen > 0) {
      long acc = 0;
      char ch;
      int val;
      for (int cnt = 5; cnt > 0; cnt--) {
        ch = data.charAt(dataOffset);
        dataOffset++;
        val = chars_85.indexOf(ch);
        if (val < 0) {
          throw new BinaryPatchException(String.format("invalid base85 symbol %c", ch));
        }
        acc = acc * 85 + val;
      }
      int cnt = (leftLen < 4) ? leftLen : 4;
      leftLen -= cnt;
      byte[] tmp = new byte[4];
      for (int i = 0; i < 4; i++) {
        tmp[3 - i] = (byte)(acc & 0xffL);
        acc >>= 8;
      }
      for (byte b : tmp) {
        result[resultOffset] = b;
        resultOffset++;
      }
    }
    return result;
  }

  static class BinaryPatchException extends Exception {
    public BinaryPatchException(String s) {
      super(s);
    }
  }
}
