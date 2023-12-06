// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.enumerator;

import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
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
  public int hashCodeOf(String value) {
    return value.hashCode();
  }

  @Override
  public boolean areEqual(String key1,
                          String key2) {
    return key1.equals(key2);
  }

  @Override
  public String read(@NotNull ByteBuffer input) throws IOException {
    return IOUtil.readString(input);
  }

  @Override
  public long saveToLog(@NotNull String key,
                        @NotNull AppendOnlyLog log) throws IOException {
    byte[] stringBytes = key.getBytes(UTF_8);
    return log.append(stringBytes);
  }
}
