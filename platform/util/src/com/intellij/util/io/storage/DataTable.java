// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

final class DataTable implements IDataTable {
  private static final Logger LOG = Logger.getInstance(DataTable.class);

  private static final int HEADER_SIZE = 32;
  private static final int DIRTY_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private final PagedFileStorage myFile;
  private volatile int myWasteSize;

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_WASTE_SIZE_OFFSET = 4;
  private volatile boolean myIsDirty;

  DataTable(@NotNull Path filePath,
            @NotNull StorageLockContext context) throws IOException {
    myFile = new PagedFileStorage(filePath, context, 8 * 1024, false, false);
    myFile.lockWrite();
    try {
    if (myFile.length() == 0) {
      markDirty();
    }
    else {
      readInHeader(filePath);
    }
  }
    finally {
      myFile.unlockWrite();
    }
  }

  @Override
  public boolean isCompactNecessary() {
    return ((double)myWasteSize) / myFile.length() > 0.25 && myWasteSize > 3 * FileUtilRt.MEGABYTE;
  }

  private void readInHeader(@NotNull Path filePath) throws IOException {
    int magic = myFile.getInt(HEADER_MAGIC_OFFSET);
    if (magic != SAFELY_CLOSED_MAGIC) {
        myFile.close();
      throw new IOException("Records table for '" + filePath + "' haven't been closed correctly. Rebuild required.");
    }
    myWasteSize = myFile.getInt(HEADER_WASTE_SIZE_OFFSET);
  }

  @Override
  public void readBytes(long address, byte[] bytes) throws IOException {
    myFile.get(address, bytes, 0, bytes.length, true);
  }

  @Override
  public void writeBytes(long address, byte[] bytes) throws IOException {
    writeBytes(address, bytes, 0, bytes.length);
  }

  @Override
  public void writeBytes(long address, byte[] bytes, int off, int len) throws IOException {
    markDirty();
    myFile.put(address, bytes, off, len);
  }

  @Override
  public long allocateSpace(int len) throws IOException {
    final long result = Math.max(myFile.length(), HEADER_SIZE);

    // Fill them in so we won't give out wrong address from allocateSpace() next time if they still not finished writing to allocated page
    long newLength = result + len;
    writeBytes(newLength - 1, new byte[]{0});
    long actualLength = myFile.length();
    if (actualLength != newLength) {
      LOG.error("Failed to resize the storage at: " + myFile + ". Required: " + newLength + ", actual: " + actualLength);
    }
    return result;
  }

  @Override
  public void reclaimSpace(int len) throws IOException {
    if (len > 0) {
      markDirty();
      myWasteSize += len;
    }
  }

  @Override
  public void close() throws IOException {
    markClean();
    myFile.close();
  }

  @Override
  public void force() throws IOException {
    markClean();
    myFile.force();
  }

  @Override
  public boolean isDirty() {
    return myIsDirty || myFile.isDirty();
  }

  private void markClean() throws IOException {
    if (myIsDirty) {
      myIsDirty = false;
      fillInHeader(SAFELY_CLOSED_MAGIC, myWasteSize);
    }
  }

  private void markDirty() throws IOException {
    if (!myIsDirty) {
      myIsDirty = true;
      fillInHeader(DIRTY_MAGIC, 0);
    }
  }

  private void fillInHeader(int magic, int wasteSize) throws IOException {
    myFile.putInt(HEADER_MAGIC_OFFSET, magic);
    myFile.putInt(HEADER_WASTE_SIZE_OFFSET, wasteSize);
  }

  @Override
  public int getWaste() {
    return myWasteSize;
  }

  @Override
  public long getFileSize() {
    return myFile.length();
  }
}
