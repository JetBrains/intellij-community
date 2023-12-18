// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.enumerator;

import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;


/** Stores/loads strings as standard UTF8 bytes */
public class StringAsUTF8 implements KeyDescriptorEx<String> {
  public static final StringAsUTF8 INSTANCE = new StringAsUTF8();

  private StringAsUTF8() {
  }

  @Override
  public int getHashCode(String value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(String key1,
                         String key2) {
    return key1.equals(key2);
  }

  @Override
  public String read(@NotNull ByteBuffer input) throws IOException {
    return IOUtil.readString(input);
  }

  @Override
  public KnownSizeRecordWriter writerFor(@NotNull String key) throws IOException {
    return KeyDescriptorEx.fromBytes(key.getBytes(UTF_8));
  }
}
