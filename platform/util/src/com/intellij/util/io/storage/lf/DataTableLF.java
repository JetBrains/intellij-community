// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage.lf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.pagecache.PagedStorage;
import com.intellij.util.io.storage.IDataTable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

//TODO RC: thread-safety is unclear
public final class DataTableLF implements IDataTable {
  private static final Logger LOG = Logger.getInstance(DataTableLF.class);

  private static final int HEADER_SIZE = 32;
  private static final int DIRTY_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private final PagedStorage file;
  private volatile int wasteSize;

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_WASTE_SIZE_OFFSET = 4;
  private volatile boolean isDirty;

  public DataTableLF(@NotNull Path filePath,
                     @NotNull StorageLockContext context) throws IOException {
    file = PagedFileStorageWithRWLockedPageContent.createWithDefaults(
      filePath,
      context,
      /*pageSize: */ 8 * 1024,
      /*nativeOrder: */ false,
      context.lockingStrategyWithGlobalLock()
    );

    if (file.length() == 0) {
      markDirty();
    }
    else {
      readInHeader(filePath);
    }
  }

  @Override
  public boolean isCompactNecessary() {
    return ((double)wasteSize) / file.length() > 0.25 && wasteSize > 3 * FileUtilRt.MEGABYTE;
  }

  private void readInHeader(@NotNull Path filePath) throws IOException {
    int magic = file.getInt(HEADER_MAGIC_OFFSET);
    if (magic != SAFELY_CLOSED_MAGIC) {
      IOException ioError = new IOException(
        "Records table for '" + filePath + "' haven't been closed correctly. Rebuild required."
      );
      try {
        file.close();
      }
      catch (IOException ioe) {
        ioError.addSuppressed(ioe);
      }
      throw ioError;
    }
    wasteSize = file.getInt(HEADER_WASTE_SIZE_OFFSET);
  }

  @Override
  public void readBytes(long address, byte[] bytes) throws IOException {
    file.get(address, bytes, 0, bytes.length);
  }

  @Override
  public void writeBytes(long address, byte[] bytes) throws IOException {
    writeBytes(address, bytes, 0, bytes.length);
  }

  @Override
  public void writeBytes(long address, byte[] bytes, int off, int len) throws IOException {
    markDirty();
    file.put(address, bytes, off, len);
  }

  @Override
  public long allocateSpace(int len) throws IOException {
    final long result = Math.max(file.length(), HEADER_SIZE);

    // Fill them in so we won't give out wrong address from allocateSpace() next time if they still not finished writing to allocated page
    long newLength = result + len;
    writeBytes(newLength - 1, new byte[]{0});
    long actualLength = file.length();
    if (actualLength != newLength) {
      LOG.error("Failed to resize the storage at: " + file + ". Required: " + newLength + ", actual: " + actualLength);
    }
    return result;
  }

  @Override
  public void reclaimSpace(int len) throws IOException {
    if (len > 0) {
      markDirty();
      wasteSize += len;
    }
  }

  @Override
  public void close() throws IOException {
    markClean();
    file.close();
  }

  @Override
  public void force() throws IOException {
    markClean();
    file.force();
  }

  @Override
  public boolean isDirty() {
    return isDirty || file.isDirty();
  }

  private void markClean() throws IOException {
    if (isDirty) {
      isDirty = false;
      fillInHeader(SAFELY_CLOSED_MAGIC, wasteSize);
    }
  }

  private void markDirty() throws IOException {
    if (!isDirty) {
      isDirty = true;
      fillInHeader(DIRTY_MAGIC, 0);
    }
  }

  private void fillInHeader(int magic, int wasteSize) throws IOException {
    file.putInt(HEADER_MAGIC_OFFSET, magic);
    file.putInt(HEADER_WASTE_SIZE_OFFSET, wasteSize);
  }

  @Override
  public int getWaste() {
    return wasteSize;
  }

  @Override
  public long getFileSize() {
    return file.length();
  }
}
