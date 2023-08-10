// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.io.keyStorage;

import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/** {@link InputStream} over {@link ResizeableMappedFile}: reads bytes through {@link ResizeableMappedFile#get(long, byte[], int, int, boolean)} */
@ApiStatus.Internal
public final class MappedFileInputStream extends InputStream {

  private final ResizeableMappedFile raf;
  private final boolean checkAccess;

  private final long limit;

  private long position;

  public MappedFileInputStream(@NotNull ResizeableMappedFile raf,
                               long position,
                               long limit,
                               boolean checkAccess) {
    long totalBytes = limit - position;
    if (totalBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("limit(=" + limit + ")-position(=" + position + ") = " + totalBytes + " > MAX_INT");
    }

    long fileLength = raf.length();
    if (limit > fileLength) {
      throw new IllegalArgumentException("limit(=" + limit + ") > file.length(=" + fileLength + ")");
    }

    this.raf = raf;
    this.position = (int)position;
    this.limit = limit;
    this.checkAccess = checkAccess;
  }

  @Override
  public int available() {
    return (int)(limit - position);
  }

  @Override
  public void close() {
    //do nothing because we want to leave the random access file open.
  }

  @Override
  public int read() throws IOException {
    if (position < limit) {
      byte b = raf.get(position, checkAccess);
      position++;
      return b & 0xFF;
    }
    return -1;
  }

  @Override
  public int read(byte @NotNull [] b, int offset, int length) throws IOException {
    //only allow a read of the amount available.
    if (length > available()) {
      length = available();
    }

    if (available() > 0) {
      raf.get(position, b, offset, length, checkAccess);
      position += length;
    }

    return length;
  }

  @Override
  public long skip(long amountToSkip) {
    long amountSkipped = Math.min(amountToSkip, available());
    position += amountSkipped;
    return amountSkipped;
  }
}
