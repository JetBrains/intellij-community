// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public final class DupOutputStream extends OutputStream {
  private final OutputStream myStream1;
  private final OutputStream myStream2;

  public DupOutputStream(@NotNull OutputStream stream1, @NotNull OutputStream stream2) {
    myStream1 = stream1;
    myStream2 = stream2;
  }

  @Override
  public void write(final int b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  @Override
  public void close() throws IOException {
    myStream1.close();
    myStream2.close();
  }

  @Override
  public void flush() throws IOException {
    myStream1.flush();
    myStream2.flush();
  }

  @Override
  public void write(final byte[] b) throws IOException {
    myStream1.write(b);
    myStream2.write(b);
  }

  @Override
  public void write(final byte[] b, final int off, final int len) throws IOException {
    myStream1.write(b, off, len);
    myStream2.write(b, off, len);
  }
}
