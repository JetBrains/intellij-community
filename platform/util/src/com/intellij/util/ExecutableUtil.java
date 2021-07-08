// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.io.RandomAccessFile;

public final class ExecutableUtil {
  private final static int PE_SIGNATURE_OFFSET_LOCATION = 0x3c;
  private final static int PE_SIGNATURE = 0x00004550;

  private ExecutableUtil() { }

  public static boolean isWinExecutable(@NotNull String path) {
    try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
      file.seek(PE_SIGNATURE_OFFSET_LOCATION);
      byte[] buf = new byte[4];
      if (file.read(buf) != buf.length) {
        return false;
      }
      int offset = readLittleEndianUint32(buf);
      file.seek(offset);
      if (file.read(buf) != buf.length) {
        return false;
      }
      return readLittleEndianUint32(buf) == PE_SIGNATURE;
    }
    catch (Exception e) {
      return false;
    }
  }

  public static int readLittleEndianUint32(byte[] buf) {
    return (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8) | ((buf[2] & 0xFF) << 16) | ((buf[3] & 0xFF) << 24);
  }
}
