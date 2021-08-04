// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

final class ProgressMonitorInputStream extends InputStream {
  private final ProgressIndicator indicator;
  private final InputStream in;

  private final double available;
  private long count;

  ProgressMonitorInputStream(@NotNull ProgressIndicator indicator, @NotNull InputStream in, int length) {
    this.indicator = indicator;
    this.in = in;
    available = length;
  }

  @Override
  public int read() throws IOException {
    int c = in.read();
    updateProgress(c >= 0 ? 1 : 0);
    return c;
  }

  private void updateProgress(long increment) {
    indicator.checkCanceled();
    if (increment > 0) {
      count += increment;
      if(!indicator.isIndeterminate()) indicator.setFraction((double)count / available);
    }
  }

  @Override
  public int read(byte[] b) throws IOException {
    int r = in.read(b);
    updateProgress(r);
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int r = in.read(b, off, len);
    updateProgress(r);
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    long r = in.skip(n);
    updateProgress(r);
    return r;
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  @Override
  public int available() throws IOException {
    return in.available();
  }
}
