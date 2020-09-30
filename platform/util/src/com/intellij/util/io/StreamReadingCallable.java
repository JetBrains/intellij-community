// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

public class StreamReadingCallable implements Callable<ByteArrayOutputStream> {
  private final InputStream myInputStream;
  private final ByteArrayOutputStream myOutputStream = new ByteArrayOutputStream();

  public StreamReadingCallable(@NotNull Process process) {
    this(process.getInputStream());
  }

  public StreamReadingCallable(InputStream inputStream) {
    myInputStream = inputStream;
  }

  @Override
  public ByteArrayOutputStream call() throws IOException {
    try (InputStream input = myInputStream; OutputStream output = myOutputStream) {
      StreamUtil.copy(input, output);
    }
    return myOutputStream;
  }
}
