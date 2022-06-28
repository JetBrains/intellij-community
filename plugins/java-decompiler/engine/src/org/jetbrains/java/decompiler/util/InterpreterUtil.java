// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.util;

import java.io.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class InterpreterUtil {
  public static final boolean IS_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

  public static final int[] EMPTY_INT_ARRAY = new int[0];

  private static final int BUFFER_SIZE = 16 * 1024;

  public static void copyFile(File source, File target) throws IOException {
    try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
      copyStream(in, out);
    }
  }

  public static void copyStream(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    int len;
    while ((len = in.read(buffer)) >= 0) {
      out.write(buffer, 0, len);
    }
  }

  public static byte[] getBytes(ZipFile archive, ZipEntry entry) throws IOException {
    try (InputStream stream = archive.getInputStream(entry)) {
      return readBytes(stream, (int)entry.getSize());
    }
  }

  public static byte[] getBytes(File file) throws IOException {
    try (FileInputStream stream = new FileInputStream(file)) {
      return readBytes(stream, (int)file.length());
    }
  }

  public static byte[] readBytes(InputStream stream, int length) throws IOException {
    byte[] bytes = new byte[length];

    int n = 0, off = 0;
    while (n < length) {
      int count = stream.read(bytes, off + n, length - n);
      if (count < 0) {
        throw new IOException("premature end of stream");
      }
      n += count;
    }

    return bytes;
  }

  public static void discardBytes(InputStream stream, int length) throws IOException {
    if (stream.skip(length) != length) {
      throw new IOException("premature end of stream");
    }
  }

  public static boolean equalSets(Collection<?> c1, Collection<?> c2) {
    if (c1 == null) {
      return c2 == null;
    }
    else if (c2 == null) {
      return false;
    }

    if (c1.size() != c2.size()) {
      return false;
    }

    HashSet<Object> set = new HashSet<>(c1);
    set.removeAll(c2);
    return (set.size() == 0);
  }

  public static String makeUniqueKey(String name, String descriptor) {
    return name + ' ' + descriptor;
  }

  public static String makeUniqueKey(String name, String descriptor1, String descriptor2) {
    return name + ' ' + descriptor1 + ' ' + descriptor2;
  }
}