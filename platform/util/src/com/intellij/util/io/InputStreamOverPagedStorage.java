// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.io;

import com.intellij.util.io.pagecache.PagedStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/** {@link InputStream} over {@link PagedStorage}: reads bytes through {@link PagedStorage#get(long, byte[], int, int)} */
@ApiStatus.Internal
public final class InputStreamOverPagedStorage extends InputStream {
  private final @NotNull PagedStorage pagedStorage;
  private final long limit;
  private long position;

  public InputStreamOverPagedStorage(@NotNull PagedStorage pagedStorage,
                                     long position,
                                     long limit) {
    long totalBytes = limit - position;
    if (totalBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("limit(=" + limit + ")-position(=" + position + ") = " + totalBytes + " > MAX_INT");
    }
    long storageLength = pagedStorage.length();
    if (limit > storageLength) {
      throw new IllegalArgumentException("limit(=" + limit + ") > storage.length(=" + storageLength + ")");
    }
    this.pagedStorage = pagedStorage;
    this.position = position;
    this.limit = limit;
  }

  @Override
  public void close() {
    //do nothing because we want to leave the paged storage open.
  }

  @Override
  public int available() {
    return (int)(limit - position);
  }

  @Override
  public int read() throws IOException {
    if (position < limit) {
      byte b = pagedStorage.get(position);
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
    pagedStorage.get(position, buffer, offset, length);
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
