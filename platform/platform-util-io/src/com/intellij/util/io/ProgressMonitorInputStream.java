// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

@ApiStatus.Internal
public final class ProgressMonitorInputStream extends InputStream {
  private final ProgressIndicator myIndicator;
  private final InputStream myStream;

  private final double myAvailable;
  private long count;

  public ProgressMonitorInputStream(@NotNull ProgressIndicator indicator, @NotNull InputStream stream, long length) {
    myIndicator = indicator;
    myStream = stream;
    myAvailable = length;
  }

  @Override
  public int read() throws IOException {
    int c = myStream.read();
    updateProgress(c >= 0 ? 1 : 0);
    return c;
  }

  private void updateProgress(long increment) {
    myIndicator.checkCanceled();
    if (increment > 0) {
      count += increment;
      if(!myIndicator.isIndeterminate()) myIndicator.setFraction((double)count / myAvailable);
    }
  }

  @Override
  public int read(byte[] b) throws IOException {
    int r = myStream.read(b);
    updateProgress(r);
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int r = myStream.read(b, off, len);
    updateProgress(r);
    return r;
  }

  @Override
  public long skip(long n) throws IOException {
    long r = myStream.skip(n);
    updateProgress(r);
    return r;
  }

  @Override
  public void close() throws IOException {
    myStream.close();
  }

  @Override
  public int available() throws IOException {
    return myStream.available();
  }
}
