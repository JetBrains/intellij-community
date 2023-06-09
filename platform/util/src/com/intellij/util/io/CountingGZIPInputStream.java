// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * A stream for reading compressed data in the GZIP file format.
 * Total amount of compressed read bytes can be accessed via {@link #getCompressedBytesRead()}.
 *
 * Note that this implementation is not thread safe.
 */
public final class CountingGZIPInputStream extends GZIPInputStream {
  private final CountingInputStream myInputStream;

  private CountingGZIPInputStream(@NotNull CountingInputStream inputStream) throws IOException {
    super(inputStream);
    myInputStream = inputStream;
  }

  public long getCompressedBytesRead() {
    return myInputStream.getBytesRead();
  }

  public static @NotNull CountingGZIPInputStream create(@NotNull InputStream inputStream) throws IOException {
    return new CountingGZIPInputStream(new CountingInputStream(inputStream));
  }
}
