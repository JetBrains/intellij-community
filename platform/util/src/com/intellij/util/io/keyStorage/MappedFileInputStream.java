// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
  public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    int bytesRemains = available();
    assert bytesRemains >= 0 : "position(=" + position + ") > limit(=" + limit + ")";
    if (bytesRemains == 0) {
      return -1;
    }

    //only allow a read of the amount remains.
    if (length > bytesRemains) {
      length = bytesRemains;
    }
    raf.get(position, buffer, offset, length, checkAccess);
    position += length;

    return length;
  }

  @Override
  public long skip(long amountToSkip) {
    long amountSkipped = Math.min(amountToSkip, available());
    position += amountSkipped;
    return amountSkipped;
  }
}
