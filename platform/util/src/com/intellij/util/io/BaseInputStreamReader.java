// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class BaseInputStreamReader extends InputStreamReader {
  private final InputStream myInputStream;

  public BaseInputStreamReader(@NotNull InputStream in) {
    super(in);
    myInputStream = in;
  }

  public BaseInputStreamReader(@NotNull InputStream in, @NotNull Charset cs) {
    super(in, cs);
    myInputStream = in;
  }

  @Override
  public void close() throws IOException {
    myInputStream.close(); // close underlying input stream without locking in StreamDecoder.
  }
}