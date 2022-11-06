// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;

/**
 * Table of indirect addressing, logically contains tuples (id, address, size, capacity), do
 * the mapping id -> (address, size, capacity), and stores tuples as fixed-size records on
 * the top of {@link PagedFileStorage}.
 * <br>
 * Subclasses could add fields to the tuple, and implement more efficient storage formats.
 * <br>
 * Thread safety is unclear: some methods are protected against concurrent access, some are not.
 */
public abstract class AbstractRecordsTable implements Closeable, Forceable {
  private static final Logger LOG = Logger.getInstance(AbstractRecordsTable.class);

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

  protected final PagedFileStorage myStorage;

  private IntList myFreeRecordsList = null;
  private boolean myIsDirty = false;
  protected static final int SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD = -1;

  public AbstractRecordsTable(@NotNull Path storageFilePath, @NotNull StorageLockContext context) throws IOException {
    myStorage = new PagedFileStorage(storageFilePath, context, getPageSize(), areDataAlignedToPage(), false);
    myStorage.lockWrite();
    try {
      if (myStorage.length() == 0) {
        myStorage.put(0, new byte[getHeaderSize()], 0, getHeaderSize());
        markDirty();
      }
      else {
        if (myStorage.getInt(HEADER_MAGIC_OFFSET) != getSafelyClosedMagic()) {
          myStorage.close();
          throw new IOException("Records table for '" + storageFilePath + "' haven't been closed correctly. Rebuild required.");
        }
      }
    }
    finally {
      myStorage.unlockWrite();
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

  public int createNewRecord() throws IOException {
    markDirty();
    ensureFreeRecordsScanned();

    int reusedRecord = reserveFreeRecord();
    if (reusedRecord == -1) {
      int result = getRecordsCount() + 1;
      doCleanRecord(result);
      if (getRecordsCount() != result) {
        LOG.error("Failed to correctly allocate new record in: " + myStorage);
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
    synchronized (myFreeRecordsList) {
      return myFreeRecordsList.isEmpty() ? -1 : myFreeRecordsList.removeInt(myFreeRecordsList.size() - 1);
    }
  }

  public int getRecordsCount() throws IOException {
    int recordsLength = (int)myStorage.length() - getHeaderSize();
    if ((recordsLength % getRecordSize()) != 0) {
      throw new IOException(MessageFormat.format("Corrupted records: storageLength={0} recordsLength={1} recordSize={2}",
                                                 myStorage.length() + " " + myStorage, recordsLength, getRecordSize()));
    }
    return recordsLength / getRecordSize();
  }

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

  @TestOnly
  public int getLiveRecordsCount() throws IOException {
    ensureFreeRecordsScanned();
    return getRecordsCount() - myFreeRecordsList.size();
  }

  private void ensureFreeRecordsScanned() throws IOException {
    if (myFreeRecordsList == null) {
      myFreeRecordsList = scanForFreeRecords();
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
    myStorage.put(getOffset(record, 0), getZeros(), 0, getRecordSize());
  }

  public long getAddress(int record) throws IOException {
    return myStorage.getLong(getOffset(record, ADDRESS_OFFSET));
  }

  public void setAddress(int record, long address) throws IOException {
    markDirty();
    myStorage.putLong(getOffset(record, ADDRESS_OFFSET), address);
  }

  public int getSize(int record) throws IOException {
    return myStorage.getInt(getOffset(record, SIZE_OFFSET));
  }

  public void setSize(int record, int size) throws IOException {
    markDirty();
    myStorage.putInt(getOffset(record, SIZE_OFFSET), size);
  }

  public int getCapacity(int record) throws IOException {
    return myStorage.getInt(getOffset(record, CAPACITY_OFFSET));
  }

  public void setCapacity(int record, int capacity) throws IOException {
    markDirty();
    myStorage.putInt(getOffset(record, CAPACITY_OFFSET), capacity);
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

  public void deleteRecord(final int record) throws IOException {
    markDirty();
    ensureFreeRecordsScanned();
    doCleanRecord(record);
    setSize(record, SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD);
    myFreeRecordsList.add(record);
  }

  public int getVersion() throws IOException {
    return myStorage.getInt(HEADER_VERSION_OFFSET);
  }

  public void setVersion(final int expectedVersion) throws IOException {
    markDirty();
    myStorage.putInt(HEADER_VERSION_OFFSET, expectedVersion);
  }

  @Override
  public void close() throws IOException {
    markClean();
    myStorage.close();
  }

  @Override
  public void force() throws IOException {
    markClean();
    myStorage.force();
  }

  @Override
  public boolean isDirty() {
    return myIsDirty || myStorage.isDirty();
  }

  public void markDirty() throws IOException {
    if (!myIsDirty) {
      myIsDirty = true;
      myStorage.putInt(HEADER_MAGIC_OFFSET, DIRTY_MAGIC);
    }
  }

  private void markClean() throws IOException {
    if (myIsDirty) {
      myIsDirty = false;
      myStorage.putInt(HEADER_MAGIC_OFFSET, getSafelyClosedMagic());
    }
  }

  protected static boolean isSizeOfRemovedRecord(int length) {
    return length == SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD;
  }

  protected static boolean isSizeOfLiveRecord(int length) {
    return length != SPECIAL_NEGATIVE_SIZE_FOR_REMOVED_RECORD;
  }
}