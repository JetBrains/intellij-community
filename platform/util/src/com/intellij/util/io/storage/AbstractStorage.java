// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RecordDataOutput;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class AbstractStorage implements Disposable, Forceable {
  protected static final Logger LOG = Logger.getInstance(Storage.class);

  @NonNls public static final String INDEX_EXTENSION = ".storageRecordIndex";
  @NonNls public static final String DATA_EXTENSION = ".storageData";

  private static final int MAX_PAGES_TO_FLUSH_AT_A_TIME = 50;

  @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
  @Deprecated
  protected final Object myLock = new Object();
  private final ReadWriteLock myScalableLock = new ReentrantReadWriteLock();
  private final boolean myUseScalableLock;

  protected AbstractRecordsTable myRecordsTable;
  protected DataTable myDataTable;
  protected PagePool myPool;
  private final CapacityAllocationPolicy myCapacityAllocationPolicy;

  public static boolean deleteFiles(String storageFilePath) {
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);
    final File dataFile = new File(storageFilePath + DATA_EXTENSION);

    // ensure both files deleted
    final boolean deletedRecordsFile = FileUtil.delete(recordsFile);
    final boolean deletedDataFile = FileUtil.delete(dataFile);
    return deletedRecordsFile && deletedDataFile;
  }

  /**
   * @deprecated please use scalable lock
   */
  @Deprecated
  protected AbstractStorage(String storageFilePath) throws IOException {
    this(storageFilePath, PagePool.SHARED, false);
  }

  protected AbstractStorage(String storageFilePath, boolean useScalableLock) throws IOException {
    this(storageFilePath, PagePool.SHARED, useScalableLock);
  }

  protected AbstractStorage(String storageFilePath, PagePool pool, boolean useScalableLock) throws IOException {
    this(storageFilePath, pool, CapacityAllocationPolicy.DEFAULT, useScalableLock);
  }

  protected AbstractStorage(String storageFilePath,
                            CapacityAllocationPolicy capacityAllocationPolicy,
                            boolean useScalableLock) throws IOException {
    this(storageFilePath, PagePool.SHARED, capacityAllocationPolicy, useScalableLock);
  }

  protected AbstractStorage(String storageFilePath,
                            PagePool pool,
                            CapacityAllocationPolicy capacityAllocationPolicy,
                            boolean useScalableLock) throws IOException {
    myCapacityAllocationPolicy = capacityAllocationPolicy != null ? capacityAllocationPolicy
                                                                  : CapacityAllocationPolicy.DEFAULT;
    tryInit(storageFilePath, pool, 0);
    myUseScalableLock = useScalableLock;
  }

  private void tryInit(String storageFilePath, PagePool pool, int retryCount) throws IOException {
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);
    final File dataFile = new File(storageFilePath + DATA_EXTENSION);

    if (recordsFile.exists() != dataFile.exists()) {
      deleteFiles(storageFilePath);
    }

    FileUtil.createIfDoesntExist(recordsFile);
    FileUtil.createIfDoesntExist(dataFile);

    AbstractRecordsTable recordsTable = null;
    DataTable dataTable;
    try {
      recordsTable = createRecordsTable(pool, recordsFile);
      dataTable = new DataTable(dataFile, pool);
    }
    catch (IOException e) {
      LOG.info(e.getMessage());
      if (recordsTable != null) {
        Disposer.dispose(recordsTable);
      }

      boolean deleted = deleteFiles(storageFilePath);
      if (!deleted) {
        throw new IOException("Can't delete caches at: " + storageFilePath);
      }
      if (retryCount >= 5) {
        throw new IOException("Can't create storage at: " + storageFilePath);
      }

      tryInit(storageFilePath, pool, retryCount+1);
      return;
    }

    myRecordsTable = recordsTable;
    myDataTable = dataTable;
    myPool = pool;

    if (myDataTable.isCompactNecessary()) {
      compact(storageFilePath);
    }
  }

  protected abstract AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException;

  private void compact(final String path) {
    withWriteLock(() -> {
      LOG.info("Space waste in " + path + " is " + myDataTable.getWaste() + " bytes. Compacting now.");
      long start = System.currentTimeMillis();

      try {
        File newDataFile = new File(path + ".storageData.backup");
        FileUtil.delete(newDataFile);
        FileUtil.createIfDoesntExist(newDataFile);

        File oldDataFile = new File(path + DATA_EXTENSION);
        DataTable newDataTable = new DataTable(newDataFile, myPool);

        RecordIdIterator recordIterator = myRecordsTable.createRecordIdIterator();
        while(recordIterator.hasNextId()) {
          final int recordId = recordIterator.nextId();
          final long addr = myRecordsTable.getAddress(recordId);
          final int size = myRecordsTable.getSize(recordId);

          if (size > 0) {
            assert addr > 0;

            final int capacity = myCapacityAllocationPolicy.calculateCapacity(size);
            final long newaddr = newDataTable.allocateSpace(capacity);
            final byte[] bytes = new byte[size];
            myDataTable.readBytes(addr, bytes);
            newDataTable.writeBytes(newaddr, bytes);
            myRecordsTable.setAddress(recordId, newaddr);
            myRecordsTable.setCapacity(recordId, capacity);
          }
        }

        Disposer.dispose(myDataTable);
        Disposer.dispose(newDataTable);

        if (!FileUtil.delete(oldDataFile)) {
          throw new IOException("Can't delete file: " + oldDataFile);
        }

        newDataFile.renameTo(oldDataFile);
        myDataTable = new DataTable(oldDataFile, myPool);
      }
      catch (IOException e) {
        LOG.info("Compact failed: " + e.getMessage());
      }

      long timedelta = System.currentTimeMillis() - start;
      LOG.info("Done compacting in " + timedelta + "msec.");
    });
  }

  public int getVersion() {
    return withReadLock(() -> {
      return myRecordsTable.getVersion();
    });
  }

  public void setVersion(int expectedVersion) {
    withWriteLock(() -> {
      myRecordsTable.setVersion(expectedVersion);
    });
  }

  @Override
  public void force() {
    withWriteLock(() -> {
      myDataTable.force();
      myRecordsTable.force();
    });
  }

  public boolean flushSome() {
    return withWriteLock(() -> {
      boolean okRecords = myRecordsTable.flushSome(MAX_PAGES_TO_FLUSH_AT_A_TIME);
      boolean okData = myDataTable.flushSome(MAX_PAGES_TO_FLUSH_AT_A_TIME);

      return okRecords && okData;
    });
  }

  @Override
  public boolean isDirty() {
    return withReadLock(() -> myDataTable.isDirty() || myRecordsTable.isDirty());
  }

  @TestOnly
  public int getLiveRecordsCount() throws IOException {
    return withReadLock(() -> myRecordsTable.getLiveRecordsCount());
  }

  @TestOnly
  public RecordIdIterator createRecordIdIterator() throws IOException {
    return myRecordsTable.createRecordIdIterator();
  }

  public StorageDataOutput writeStream(final int record) {
    return writeStream(record, false);
  }
  public StorageDataOutput writeStream(final int record, boolean fixedSize) {
    return new StorageDataOutput(this, record, fixedSize);
  }

  public AppenderStream appendStream(int record) {
    return new AppenderStream(record);
  }

  public DataInputStream readStream(int record) throws IOException {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new UnsyncByteArrayInputStream(bytes));
  }

  protected byte[] readBytes(int record) throws IOException {
    return withReadLock(() -> {
      final int length = myRecordsTable.getSize(record);
      if (length == 0 || AbstractRecordsTable.isSizeOfRemovedRecord(length)) return ArrayUtilRt.EMPTY_BYTE_ARRAY;
      assert length > 0:length;

      final long address = myRecordsTable.getAddress(record);
      byte[] result = new byte[length];
      myDataTable.readBytes(address, result);
      return result;
    });
  }

  protected void appendBytes(int record, ByteArraySequence bytes) throws IOException {
    final int delta = bytes.getLength();
    if (delta == 0) return;
    withWriteLock(() -> {
      int capacity = myRecordsTable.getCapacity(record);
      int oldSize = myRecordsTable.getSize(record);
      int newSize = oldSize + delta;
      if (newSize > capacity) {
        if (oldSize > 0) {
          final byte[] newbytes = new byte[newSize];
          System.arraycopy(readBytes(record), 0, newbytes, 0, oldSize);
          System.arraycopy(bytes.getBytes(), bytes.getOffset(), newbytes, oldSize, delta);
          writeBytes(record, new ByteArraySequence(newbytes), false);
        }
        else {
          writeBytes(record, bytes, false);
        }
      }
      else {
        long address = myRecordsTable.getAddress(record) + oldSize;
        myDataTable.writeBytes(address, bytes.getBytes(), bytes.getOffset(), bytes.getLength());
        myRecordsTable.setSize(record, newSize);
      }
    });
  }

  public void writeBytes(int record, ByteArraySequence bytes, boolean fixedSize) throws IOException {
    withWriteLock(() -> {
      final int requiredLength = bytes.getLength();
      final int currentCapacity = myRecordsTable.getCapacity(record);

      final int currentSize = myRecordsTable.getSize(record);
      assert currentSize >= 0;

      if (requiredLength == 0 && currentSize == 0) return;

      final long address;
      if (currentCapacity >= requiredLength) {
        address = myRecordsTable.getAddress(record);
      }
      else {
        myDataTable.reclaimSpace(currentCapacity);

        int newCapacity = fixedSize ? requiredLength:myCapacityAllocationPolicy.calculateCapacity(requiredLength);
        if (newCapacity < requiredLength) newCapacity = requiredLength;
        address = myDataTable.allocateSpace(newCapacity);
        myRecordsTable.setAddress(record, address);
        myRecordsTable.setCapacity(record, newCapacity);
      }

      myDataTable.writeBytes(address, bytes.getBytes(), bytes.getOffset(), bytes.getLength());
      myRecordsTable.setSize(record, requiredLength);
    });
  }

  protected void doDeleteRecord(int record) throws IOException {
    myDataTable.reclaimSpace(myRecordsTable.getCapacity(record));
    myRecordsTable.deleteRecord(record);
  }

  @Override
  public void dispose() {
    withWriteLock(() -> {
      Disposer.dispose(myRecordsTable);
      Disposer.dispose(myDataTable);
    });
  }

  public void checkSanity(final int record) {
    withReadLock(() -> {
      final int size = myRecordsTable.getSize(record);
      assert size >= 0;
      final long address = myRecordsTable.getAddress(record);
      assert address >= 0;
      assert address + size < myDataTable.getFileSize();
    });
  }

  public void replaceBytes(int record, int offset, ByteArraySequence bytes) {
    withWriteLock(() -> {
      final int changedBytesLength = bytes.getLength();

      final int currentSize = myRecordsTable.getSize(record);
      assert currentSize >= 0;
      assert offset + bytes.getLength() <= currentSize;

      if (changedBytesLength == 0) return;

      final long address = myRecordsTable.getAddress(record);

      myDataTable.writeBytes(address + offset, bytes.getBytes(), bytes.getOffset(), bytes.getLength());
    });
  }

  public static final class StorageDataOutput extends DataOutputStream implements RecordDataOutput {
    private final AbstractStorage myStorage;
    private final int myRecordId;
    private final boolean myFixedSize;

    private StorageDataOutput(AbstractStorage storage, int recordId, boolean fixedSize) {
      super(new BufferExposingByteArrayOutputStream());
      myStorage = storage;
      myRecordId = recordId;
      myFixedSize = fixedSize;
    }

    @Override
    public void close() throws IOException {
      super.close();
      final BufferExposingByteArrayOutputStream byteStream = getByteStream();
      myStorage.writeBytes(myRecordId, byteStream.toByteArraySequence(), myFixedSize);
    }

    protected BufferExposingByteArrayOutputStream getByteStream() {
      return ((BufferExposingByteArrayOutputStream)out);
    }

    @Override
    public int getRecordId() {
      return myRecordId;
    }
  }

  public final class AppenderStream extends DataOutputStream {
    private final int myRecordId;

    private AppenderStream(int recordId) {
      super(new BufferExposingByteArrayOutputStream());
      myRecordId = recordId;
    }

    @Override
    public void close() throws IOException {
      super.close();
      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
      appendBytes(myRecordId, _out.toByteArraySequence());
    }
  }

  protected <T, E extends Throwable> T withReadLock(@NotNull ThrowableComputable<T, E> runnable) throws E {
    if (myUseScalableLock) {
      return ConcurrencyUtil.withLock(myScalableLock.readLock(), runnable);
    }
    else {
      synchronized (myLock) {
        return runnable.compute();
      }
    }
  }

  protected <E extends Throwable> void withReadLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    if (myUseScalableLock) {
      ConcurrencyUtil.withLock(myScalableLock.readLock(), runnable);
    }
    else {
      synchronized (myLock) {
        runnable.run();
      }
    }
  }

  protected  <T, E extends Throwable> T withWriteLock(@NotNull ThrowableComputable<T, E> runnable) throws E {
    if (myUseScalableLock) {
      return ConcurrencyUtil.withLock(myScalableLock.writeLock(), runnable);
    }
    else {
      synchronized (myLock) {
        return runnable.compute();
      }
    }
  }

  protected <E extends Throwable> void withWriteLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    if (myUseScalableLock) {
      ConcurrencyUtil.withLock(myScalableLock.writeLock(), runnable);
    }
    else {
      synchronized (myLock) {
        runnable.run();
      }
    }
  }
}
