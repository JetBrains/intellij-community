// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.xxh3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

final class StringHash {
  private static final MethodHandle valueGetter;
  private static final boolean enableCompactStrings;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
      valueGetter = lookup.findGetter(String.class, "value", byte[].class);
      byte[] value = (byte[])valueGetter.invokeExact("A");
      enableCompactStrings = value.length == 1;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  static long longHash(String s, int offset, int length, long seed) {
    int totalLength = s.length();
    byte[] value;
    try {
      value = (byte[])valueGetter.invokeExact(s);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }

    if (enableCompactStrings && value.length == totalLength) {
      // the UTF-8 representation is exactly equivalent to ASCII
      return Xxh3Impl.hash(value, ByteArrayAccess.INSTANCE, offset, length, seed);
    }
    else {
      // value in UTF_16LE - convert to UTF_8
      byte[] data = s.substring(offset, offset + length).getBytes(StandardCharsets.UTF_8);
      return Xxh3Impl.hash(data, ByteArrayAccess.INSTANCE, 0, data.length, seed);
    }
  }
}
