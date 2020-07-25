// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RandomAccessDataFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

final class DataTable implements Disposable, Forceable {
  private static final Logger LOG = Logger.getInstance(DataTable.class);

  private static final int HEADER_SIZE = 32;
  private static final int DIRTY_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private final RandomAccessDataFile myFile;
  private volatile int myWasteSize;

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_WASTE_SIZE_OFFSET = 4;
  private boolean myIsDirty = false;

  DataTable(@NotNull Path filePath, @NotNull PagePool pool) throws IOException {
    myFile = new RandomAccessDataFile(filePath, pool);
    if (myFile.length() == 0) {
      markDirty();
    }
    else {
      readInHeader(filePath);
    }
  }

  public boolean isCompactNecessary() {
    return ((double)myWasteSize)/myFile.length() > 0.25 && myWasteSize > 3 * FileUtilRt.MEGABYTE;
  }

  private void readInHeader(@NotNull Path filePath) throws IOException {
    int magic = myFile.getInt(HEADER_MAGIC_OFFSET);
    if (magic != SAFELY_CLOSED_MAGIC) {
      myFile.dispose();
      throw new IOException("Records table for '" + filePath + "' haven't been closed correctly. Rebuild required.");
    }
    myWasteSize = myFile.getInt(HEADER_WASTE_SIZE_OFFSET);
  }

  public void readBytes(long address, byte[] bytes) {
    myFile.get(address, bytes, 0, bytes.length);
  }

  public void writeBytes(long address, byte[] bytes) {
    writeBytes(address, bytes, 0, bytes.length);
  }

  public void writeBytes(long address, byte[] bytes, int off, int len) {
    markDirty();
    myFile.put(address, bytes, off, len);
  }

  public long allocateSpace(int len) {
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

  public void reclaimSpace(int len) {
    if (len > 0) {
      markDirty();
      myWasteSize += len;
    }
  }

  @Override
  public void dispose() {
    if (!myFile.isDisposed()) {
      markClean();
      myFile.dispose();
    }
  }

  @Override
  public void force() {
    markClean();
    myFile.force();
  }

  public boolean flushSome(int maxPages) {
    myFile.flushSomePages(maxPages);
    if (!myFile.isDirty()) {
      force();
      return true;
    }
    return false;
  }

  @Override
  public boolean isDirty() {
    return myIsDirty || myFile.isDirty();
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = false;
      fillInHeader(SAFELY_CLOSED_MAGIC, myWasteSize);
    }
  }

  private void markDirty() {
    if (!myIsDirty) {
      myIsDirty = true;
      fillInHeader(DIRTY_MAGIC, 0);
    }
  }

  private void fillInHeader(int magic, int wasteSize) {
    myFile.putInt(HEADER_MAGIC_OFFSET, magic);
    myFile.putInt(HEADER_WASTE_SIZE_OFFSET, wasteSize);
  }

  public int getWaste() {
    return myWasteSize;
  }

  public long getFileSize() {
    return myFile.length();
  }
}
