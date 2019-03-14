// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public interface Hasher {
  void append(@NotNull byte[] bytes);

  default void append(@NotNull String string) {
    append(string.getBytes(Charset.defaultCharset()));
  }

  default void append(int number) {
    byte[] bytes = new byte[4];
    bytes[0] = (byte) (number >> 24);
    bytes[1] = (byte) (number >> 16);
    bytes[2] = (byte) (number >> 8);
    bytes[3] = (byte) (number);
    append(bytes);
  }
}
