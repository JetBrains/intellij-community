// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage.lf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.pagecache.PagedStorage;
import com.intellij.util.io.pagecache.PagedStorageWithPageUnalignedAccess;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.util.io.storage.IRecordsTable;
import com.intellij.util.io.storage.RecordIdIterator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Table of indirect addressing, logically contains tuples (id, address, size, capacity), do
 * the mapping id -> (address, size, capacity), and stores tuples as fixed-size records on
 * the top of {@link PagedStorage}.
 * <br>
 * Subclasses could add fields to the tuple, and implement more efficient storage formats.
 * <br>
 * TODO Thread safety is unclear: some methods are protected against concurrent access, some are not.
 */
public abstract class AbstractRecordsTableLF implements IRecordsTable {
  private static final Logger LOG = Logger.getInstance(AbstractRecordsTableLF.class);

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_VERSION_OFFSET = 4;
  protected static final int DEFAULT_HEADER_SIZE = 8;

  private static final int VERSION = 5;
  private static final int DIRTY_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f + VERSION;

  private static final int ADDRESS_OFFSET = 0;
  private static final int SIZE_OFFSET = ADDRESS_OFFSET + 8;
  private static final int CAPACITY_OFFSET = SIZE_OFFSET + 4;

  protected static final int DEFAULT_RECORD_SIZE = CAPACITY_OFFSET + 4;

  protected static final int SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD = -1;


  protected final PagedStorage storage;

  private IntList freeRecordsList = null;
  private boolean isDirty = false;


  public AbstractRecordsTableLF(@NotNull Path storageFilePath, @NotNull StorageLockContext context) throws IOException {
    PagedFileStorageWithRWLockedPageContent storage = PagedFileStorageWithRWLockedPageContent.createWithDefaults(
      storageFilePath,
      context,
      getPageSize(),
      false,
      context.lockingStrategyWithGlobalLock()
    );
    if (areDataAlignedToPage()) {
      this.storage = storage;
    }
    else {
      this.storage = new PagedStorageWithPageUnalignedAccess(storage);
    }

    if (this.storage.length() == 0) {
      this.storage.put(0, new byte[getHeaderSize()], 0, getHeaderSize());
      markDirty();
    }
    else {
      if (this.storage.getInt(HEADER_MAGIC_OFFSET) != getSafelyClosedMagic()) {
        final IOException ioError =
          new IOException("Records table for '" + storageFilePath + "' haven't been closed correctly. Rebuild required.");
        try {
          this.storage.close();
        }
        catch (IOException ioe) {
          ioError.addSuppressed(ioe);
        }
        throw ioError;
      }
    }
  }

  private static int getPageSize() {
    return AbstractStorage.PAGE_SIZE;
  }

  private boolean areDataAlignedToPage() {
    return ((getPageSize() - getHeaderSize()) % getRecordSize() == 0) && (getPageSize() % getRecordSize() == 0);
  }

  private int getSafelyClosedMagic() {
    return SAFELY_CLOSED_MAGIC + getImplVersion();
  }

  protected int getHeaderSize() {
    return DEFAULT_HEADER_SIZE;
  }

  protected abstract int getImplVersion();

  protected abstract int getRecordSize();

  protected abstract byte[] getZeros();

  @Override
  public int createNewRecord() throws IOException {
    markDirty();
    ensureFreeRecordsScanned();

    int reusedRecord = reserveFreeRecord();
    if (reusedRecord == -1) {
      int result = getRecordsCount() + 1;
      doCleanRecord(result);
      if (getRecordsCount() != result) {
        LOG.error("Failed to correctly allocate new record in: " + storage);
      }
      return result;
    }
    else {
      assert isSizeOfRemovedRecord(getSize(reusedRecord));
      setSize(reusedRecord, 0);
      setCapacity(reusedRecord, 0);
      return reusedRecord;
    }
  }

  private int reserveFreeRecord() throws IOException {
    ensureFreeRecordsScanned();
    synchronized (freeRecordsList) {
      return freeRecordsList.isEmpty() ? -1 : freeRecordsList.removeInt(freeRecordsList.size() - 1);
    }
  }

  @Override
  public int getRecordsCount() throws IOException {
    int recordsLength = (int)storage.length() - getHeaderSize();
    if ((recordsLength % getRecordSize()) != 0) {
      throw new CorruptedException(
        "Corrupted records: storageLength=" + storage.length() + " recordsLength=" + recordsLength + " recordSize=" + getRecordSize()
      );
    }
    return recordsLength / getRecordSize();
  }

  @Override
  public RecordIdIterator createRecordIdIterator() throws IOException {
    return new RecordIdIterator() {
      private final int count = getRecordsCount();
      private int recordId = 1;

      @Override
      public boolean hasNextId() {
        return recordId <= count;
      }

      @Override
      public int nextId() {
        assert hasNextId();
        return recordId++;
      }

      @Override
      public boolean validId() throws IOException {
        assert hasNextId();
        return isSizeOfLiveRecord(getSize(recordId));
      }
    };
  }

  @Override
  @TestOnly
  public int getLiveRecordsCount() throws IOException {
    ensureFreeRecordsScanned();
    return getRecordsCount() - freeRecordsList.size();
  }

  private void ensureFreeRecordsScanned() throws IOException {
    if (freeRecordsList == null) {
      freeRecordsList = scanForFreeRecords();
    }
  }

  private IntList scanForFreeRecords() throws IOException {
    IntList result = new IntArrayList();
    for (int i = 1; i <= getRecordsCount(); i++) {
      if (isSizeOfRemovedRecord(getSize(i))) {
        result.add(i);
      }
    }
    return result;
  }

  private void doCleanRecord(int record) throws IOException {
    storage.put(getOffset(record, 0), getZeros(), 0, getRecordSize());
  }

  @Override
  public long getAddress(int record) throws IOException {
    return storage.getLong(getOffset(record, ADDRESS_OFFSET));
  }

  @Override
  public void setAddress(int record, long address) throws IOException {
    markDirty();
    storage.putLong(getOffset(record, ADDRESS_OFFSET), address);
  }

  @Override
  public int getSize(int record) throws IOException {
    return storage.getInt(getOffset(record, SIZE_OFFSET));
  }

  @Override
  public void setSize(int record, int size) throws IOException {
    markDirty();
    storage.putInt(getOffset(record, SIZE_OFFSET), size);
  }

  @Override
  public int getCapacity(int record) throws IOException {
    return storage.getInt(getOffset(record, CAPACITY_OFFSET));
  }

  @Override
  public void setCapacity(int record, int capacity) throws IOException {
    markDirty();
    storage.putInt(getOffset(record, CAPACITY_OFFSET), capacity);
  }

  protected int getOffset(int record, int section) {
    assert record > 0 : "record = " + record;
    int offset = getHeaderSize() + (record - 1) * getRecordSize() + section;
    if (offset < 0) {
      throw new IllegalArgumentException("offset is negative (" + offset + "): " +
                                         "record = " + record + ", " +
                                         "section " + section + ", " +
                                         "header size " + getHeaderSize() + ", " +
                                         "record size = " + getRecordSize());
    }
    return offset;
  }

  @Override
  public void deleteRecord(final int record) throws IOException {
    markDirty();
    ensureFreeRecordsScanned();
    doCleanRecord(record);
    setSize(record, SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD);
    freeRecordsList.add(record);
  }

  @Override
  public int getVersion() throws IOException {
    return storage.getInt(HEADER_VERSION_OFFSET);
  }

  @Override
  public void setVersion(final int expectedVersion) throws IOException {
    markDirty();
    storage.putInt(HEADER_VERSION_OFFSET, expectedVersion);
  }

  @Override
  public void close() throws IOException {
    markClean();
    storage.close();
  }

  @Override
  public void force() throws IOException {
    markClean();
    storage.force();
  }

  @Override
  public boolean isDirty() {
    return isDirty || storage.isDirty();
  }

  @Override
  public void markDirty() throws IOException {
    if (!isDirty) {
      isDirty = true;
      storage.putInt(HEADER_MAGIC_OFFSET, DIRTY_MAGIC);
    }
  }

  private void markClean() throws IOException {
    if (isDirty) {
      isDirty = false;
      storage.putInt(HEADER_MAGIC_OFFSET, getSafelyClosedMagic());
    }
  }

  protected static boolean isSizeOfRemovedRecord(int length) {
    return length == SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD;
  }

  protected static boolean isSizeOfLiveRecord(int length) {
    return length != SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD;
  }
}