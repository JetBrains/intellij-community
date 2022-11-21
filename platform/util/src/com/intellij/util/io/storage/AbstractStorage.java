// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public abstract class AbstractStorage implements Disposable, Forceable {
  public static final StorageLockContext SHARED = new StorageLockContext(true, true);
  public static final int PAGE_SIZE = SystemProperties.getIntProperty("idea.io.page.size", 8 * 1024);

  protected static final Logger LOG = Logger.getInstance(AbstractStorage.class);

  @NonNls public static final String INDEX_EXTENSION = ".storageRecordIndex";
  @NonNls public static final String DATA_EXTENSION = ".storageData";

  protected AbstractRecordsTable myRecordsTable;
  protected DataTable myDataTable;
  protected StorageLockContext myContext;
  private final CapacityAllocationPolicy myCapacityAllocationPolicy;

  public static boolean deleteFiles(String storageFilePath) {
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);
    final File dataFile = new File(storageFilePath + DATA_EXTENSION);

    // ensure both files deleted
    final boolean deletedRecordsFile = FileUtil.delete(recordsFile);
    final boolean deletedDataFile = FileUtil.delete(dataFile);
    return deletedRecordsFile && deletedDataFile;
  }

  public static boolean deleteFiles(@NotNull Path storageFilePath) {
    Path recordsFile = storageFilePath.getParent().resolve(storageFilePath.getFileName() + INDEX_EXTENSION);
    Path dataFile = storageFilePath.getParent().resolve(storageFilePath.getFileName() + DATA_EXTENSION);

    // ensure both files deleted
    boolean deletedRecordsFile = false;
    try {
      deletedRecordsFile = Files.deleteIfExists(recordsFile);
    }
    catch (IOException ignore) {
    }
    boolean deletedDataFile = false;
    try {
      deletedDataFile = Files.deleteIfExists(dataFile);
    }
    catch (IOException ignore) {
    }
    return deletedRecordsFile && deletedDataFile;
  }

  protected AbstractStorage(@NotNull Path storageFilePath) throws IOException {
    this(storageFilePath, SHARED);
  }

  protected AbstractStorage(@NotNull Path storageFilePath, @NotNull StorageLockContext context) throws IOException {
    this(storageFilePath, context, CapacityAllocationPolicy.DEFAULT);
  }

  protected AbstractStorage(@NotNull Path storageFilePath,
                            CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    this(storageFilePath, SHARED, capacityAllocationPolicy);
  }

  protected AbstractStorage(@NotNull Path storageFilePath,
                            @NotNull StorageLockContext context,
                            @Nullable CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    myCapacityAllocationPolicy = capacityAllocationPolicy != null ? capacityAllocationPolicy
                                                                  : CapacityAllocationPolicy.DEFAULT;
    tryInit(storageFilePath, context, 0);
  }

  private void tryInit(@NotNull Path storageFilePath, StorageLockContext context, int retryCount) throws IOException {
    Path parentDir = storageFilePath.getParent();
    Path recordsFile = parentDir.resolve(storageFilePath.getFileName() + INDEX_EXTENSION);
    Path dataFile = parentDir.resolve(storageFilePath.getFileName() + DATA_EXTENSION);

    boolean rFExists = Files.exists(recordsFile);
    boolean dFExists = Files.exists(dataFile);
    if (rFExists != dFExists) {
      // ensure both files deleted
      rFExists = false;
      dFExists = false;
    }

    if (!rFExists) {
      Files.createDirectories(parentDir);
    }

    if (!rFExists) {
      createOrTruncateFile(recordsFile);
    }
    if (!dFExists) {
      createOrTruncateFile(dataFile);
    }

    AbstractRecordsTable recordsTable = null;
    DataTable dataTable;
    try {
      recordsTable = createRecordsTable(context, recordsFile);
      dataTable = new DataTable(dataFile, context);
    }
    catch (IOException e) {
      LOG.info(e.getMessage());
      if (recordsTable != null) {
        IOUtil.closeSafe(LOG, recordsTable);
      }

      boolean deleted = deleteFiles(storageFilePath);
      if (!deleted) {
        throw new IOException("Can't delete caches at: " + storageFilePath);
      }
      if (retryCount >= 5) {
        throw new IOException("Can't create storage at: " + storageFilePath);
      }

      tryInit(storageFilePath, context, retryCount+1);
      return;
    }

    myRecordsTable = recordsTable;
    myDataTable = dataTable;
    myContext = context;

    if (myDataTable.isCompactNecessary()) {
      compact(storageFilePath);
    }
  }

  protected abstract AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext context, @NotNull Path recordsFile) throws IOException;

  private void compact(@NotNull Path path) {
    withWriteLock(() -> {
      LOG.info("Space waste in " + path + " is " + myDataTable.getWaste() + " bytes. Compacting now.");
      long start = System.currentTimeMillis();

      try {
        Path parentDir = path.getParent();
        Path newDataFile = parentDir.resolve(path.getFileName() + ".storageData.backup");
        Files.createDirectories(parentDir);
        createOrTruncateFile(newDataFile);

        Path oldDataFile = parentDir.resolve(path.getFileName() + DATA_EXTENSION);
        DataTable newDataTable = new DataTable(newDataFile, myContext);

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

        myDataTable.close();
        newDataTable.close();

        Files.move(newDataFile, oldDataFile, StandardCopyOption.REPLACE_EXISTING);
        myDataTable = new DataTable(oldDataFile, myContext);
      }
      catch (IOException e) {
        LOG.info("Compact failed", e);
      }

      long timedelta = System.currentTimeMillis() - start;
      LOG.info("Done compacting in " + timedelta + "msec.");
    });
  }

  private static void createOrTruncateFile(@NotNull Path path) throws IOException {
    Files.newByteChannel(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).close();
  }

  public int getVersion() throws IOException {
    return withReadLock(() -> {
      return myRecordsTable.getVersion();
    });
  }

  public void setVersion(int expectedVersion) throws IOException {
    withWriteLock(() -> {
      myRecordsTable.setVersion(expectedVersion);
    });
  }

  @Override
  public void force() throws IOException {
    withWriteLock(() -> {
      myDataTable.force();
      myRecordsTable.force();
    });
  }

  @Override
  public boolean isDirty() {
    return myDataTable.isDirty() || myRecordsTable.isDirty();
  }

  @TestOnly
  public int getLiveRecordsCount() throws IOException {
    return withReadLock(() -> myRecordsTable.getLiveRecordsCount());
  }

  @TestOnly
  public RecordIdIterator createRecordIdIterator() throws IOException {
    myRecordsTable.myStorage.lockWrite();
    try {
      return myRecordsTable.createRecordIdIterator();
    }
    finally {
      myRecordsTable.myStorage.unlockWrite();
    }
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
          System.arraycopy(bytes.getInternalBuffer(), bytes.getOffset(), newbytes, oldSize, delta);
          writeBytes(record, new ByteArraySequence(newbytes), false);
        }
        else {
          writeBytes(record, bytes, false);
        }
      }
      else {
        long address = myRecordsTable.getAddress(record) + oldSize;
        myDataTable.writeBytes(address, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
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

      myDataTable.writeBytes(address, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
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
      IOUtil.closeSafe(LOG, myRecordsTable);
      IOUtil.closeSafe(LOG, myDataTable);
    });
  }

  public void checkSanity(final int record) throws IOException {
    withReadLock(() -> {
      final int size = myRecordsTable.getSize(record);
      assert size >= 0;
      final long address = myRecordsTable.getAddress(record);
      assert address >= 0;
      assert address + size < myDataTable.getFileSize();
    });
  }

  public void replaceBytes(int record, int offset, ByteArraySequence bytes) throws IOException {
    withWriteLock(() -> {
      final int changedBytesLength = bytes.getLength();

      final int currentSize = myRecordsTable.getSize(record);
      assert currentSize >= 0;
      assert offset + bytes.getLength() <= currentSize;

      if (changedBytesLength == 0) return;

      final long address = myRecordsTable.getAddress(record);

      myDataTable.writeBytes(address + offset, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
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

    private BufferExposingByteArrayOutputStream getByteStream() {
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
    return ConcurrencyUtil.withLock(myContext.readLock(), runnable);
  }

  protected <E extends Throwable> void withReadLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    ConcurrencyUtil.withLock(myContext.readLock(), runnable);
  }

  protected  <T, E extends Throwable> T withWriteLock(@NotNull ThrowableComputable<T, E> runnable) throws E {
    return ConcurrencyUtil.withLock(myContext.writeLock(), runnable);
  }

  protected <E extends Throwable> void withWriteLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    ConcurrencyUtil.withLock(myContext.writeLock(), runnable);
  }
}
