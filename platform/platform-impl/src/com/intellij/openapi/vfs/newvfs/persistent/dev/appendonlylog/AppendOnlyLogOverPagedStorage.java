// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferWriter;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PageUnsafe;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class AppendOnlyLogOverPagedStorage implements AppendOnlyLog {

  private final PagedFileStorageWithRWLockedPageContent storage;

  private final AtomicLong allocatedOffset = new AtomicLong();
  private final AtomicLong committedOffset = new AtomicLong();

  public AppendOnlyLogOverPagedStorage(@NotNull PagedFileStorageWithRWLockedPageContent storage) {
    this.storage = storage;
  }

  @Override
  public long append(@NotNull ByteBufferWriter writer,
                     int recordSize) throws IOException {
    //FIXME RC: allocateRecord(recordSize) -- alignment, page borders, etc..
    //          locking, etc
    long startingOffset = allocatedOffset.getAndAdd(recordSize);
    int offsetInPage = storage.toOffsetInPage(startingOffset);
    try (Page page = storage.pageByOffset(startingOffset, /*forModification: */ true)) {
      page.write(offsetInPage, recordSize, buffer -> writer.write(buffer));
    }
    return offsetToId(startingOffset);
  }

  @Override
  public <T> T read(long recordId,
                    @NotNull ByteBufferReader<T> reader) throws IOException {
    //FIXME RC: locking, etc
    long startingOffsetInFile = idToOffset(recordId);
    int offsetInPage = storage.toOffsetInPage(startingOffsetInFile);
    try (Page page = storage.pageByOffset(startingOffsetInFile, /*forModification: */ false)) {
      ByteBuffer pageBuffer = ((PageUnsafe)page).rawPageBuffer();
      int recordSize = pageBuffer.getInt(offsetInPage);
      return reader.read(pageBuffer.slice(offsetInPage + 4, recordSize));
    }
  }

  @Override
  public boolean forEachRecord(@NotNull RecordReader reader) throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  private long offsetToId(long offset) {
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  private long idToOffset(long id) {
    throw new UnsupportedOperationException("Method not implemented yet");
  }
}
