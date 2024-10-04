// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diff.impl.patch.lib.base85xjava.Base85x;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
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

@ApiStatus.Internal
public final class BinaryEncoder {

  private static char getCharForLineSize(int lineSize) throws BinaryPatchException, Base85x.Base85FormatException {
    checkLenIsValid(lineSize, VcsBundle.message("patch.binary.decoder.line.error"));
    return encodeChar(decodeChar('A') + lineSize - 1);
  }

  private static int getLineSizeFromChar(char charSize) throws BinaryPatchException, Base85x.Base85FormatException {
    int result = decodeChar(charSize) - decodeChar('A') + 1;
    checkLenIsValid(result, VcsBundle.message("patch.binary.decoder.char.error"));
    return result;
  }

  private static void checkLenIsValid(int len, @NotNull String errorMessage) throws BinaryPatchException {
    if (len < 0 || len > 52) {
      throw new BinaryPatchException(errorMessage);
    }
  }

  public static void encode(@NotNull InputStream input, @NotNull Writer writer) throws IOException, BinaryPatchException {
    int maxLineSize = 52;
    byte[] deflated = new byte[maxLineSize];
    try (DeflaterInputStream deflaterStream = new DeflaterInputStream(input)) {
      int lineSize;
      while (true) {
        lineSize = deflaterStream.read(deflated, 0, maxLineSize);
        if (lineSize <= 0) break;
        writer.append(getCharForLineSize(lineSize));
        //fill encoded block to be divisible by 4
        int newSize = ((lineSize + 3) / 4) * 4;
        Arrays.fill(deflated, lineSize, newSize, (byte)0);
        writer.append(new String(Base85x.encode(deflated, newSize)));
        writer.append('\n');
      }

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
            throw new BinaryPatchException(VcsBundle.message("patch.binary.decoder.decompress.error"));
          }
          output.write(inflated, 0, resultLength);
        }
        if (!input.hasNext()) break;
        line = input.next();
      }
      int count = output.size();
      if (count != size) {
        throw new BinaryPatchException(VcsBundle.message("patch.binary.decoder.content.error", size > count ? 0 : 1));
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
