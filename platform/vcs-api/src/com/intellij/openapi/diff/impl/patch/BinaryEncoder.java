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

import com.intellij.openapi.diff.impl.patch.lib.base85xjava.Base85x;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterInputStream;
import java.util.zip.Inflater;

import static com.intellij.openapi.diff.impl.patch.lib.base85xjava.Base85x.decodeChar;
import static com.intellij.openapi.diff.impl.patch.lib.base85xjava.Base85x.encodeChar;

public class BinaryEncoder {

  private static char getCharForLineSize(int lineSize) throws BinaryPatchException, Base85x.Base85FormatException {
    checkLenIsValid(lineSize, "Can't encode binary file patch: wrong line size");
    return encodeChar(decodeChar('A') + lineSize - 1);
  }

  private static int getLineSizeFromChar(char charSize) throws BinaryPatchException, Base85x.Base85FormatException {
    int result = decodeChar(charSize) - decodeChar('A') + 1;
    checkLenIsValid(result, "Can't decode binary file patch: wrong char-size symbol");
    return result;
  }

  private static void checkLenIsValid(int len, @NotNull String errorMessage) throws BinaryPatchException {
    if (len < 0 && len > 52) {
      throw new BinaryPatchException(errorMessage);
    }
  }

  public static void encode(@NotNull InputStream input, @NotNull Writer writer) throws IOException, BinaryPatchException {
    int maxLineSize = 52;
    byte[] deflated = new byte[maxLineSize];
    try (DeflaterInputStream deflaterStream = new DeflaterInputStream(input)) {
      int lineSize;
      do {
        lineSize = deflaterStream.read(deflated, 0, maxLineSize);
        if (lineSize <= 0) break;
        writer.append(getCharForLineSize(lineSize));
        //fill encoded block to be divisible by 4
        int newSize = ((lineSize + 3) / 4) * 4;
        Arrays.fill(deflated, lineSize, newSize, (byte)0);
        writer.append(new String(Base85x.encode(deflated, newSize)));
        writer.append('\n');
      }
      while (lineSize > 0);
    }
    catch (Base85x.Base85FormatException e) {
      throw new BinaryPatchException(e);
    }
  }

  public static void decode(@NotNull ListIterator<String> input, long size, @NotNull ByteArrayOutputStream output)
    throws BinaryPatchException {
    Inflater inflater = new Inflater();
    byte[] inflated = new byte[1024];
    try {
      String line = input.next();
      while (line != null && line.length() > 0) {
        int len = getLineSizeFromChar(line.charAt(0));
        byte[] toInflate = Base85x.decode(line.substring(1));
        inflater.setInput(toInflate, 0, len);
        int resultLength;
        while (!inflater.needsInput()) {
          try {
            resultLength = inflater.inflate(inflated);
          }
          catch (DataFormatException e) {
            throw new BinaryPatchException("Can't decode binary file patch: can't decompress data");
          }
          output.write(inflated, 0, resultLength);
        }
        if (!input.hasNext()) break;
        line = input.next();
      }
      int count = output.size();
      if (count != size) {
        throw new BinaryPatchException(String.format("Length of decoded binary patch mismatches: expected %d, received %d", size, count));
      }
    }
    catch (Base85x.Base85FormatException e) {
      throw new BinaryPatchException(e);
    }
    finally {
      inflater.end();
    }
  }

  public static class BinaryPatchException extends Exception {
    BinaryPatchException(String s) {
      super(s);
    }

    BinaryPatchException(Base85x.Base85FormatException e) {
      this(e.getMessage());
    }
  }
}
