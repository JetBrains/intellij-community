// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage.lf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.*;
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

public abstract class AbstractStorageLF implements IStorage, CleanableStorage {
  public static final StorageLockContext SHARED = new StorageLockContext(true, true);
  public static final int PAGE_SIZE = SystemProperties.getIntProperty("idea.io.page.size", 8 * 1024);

  protected static final Logger LOG = Logger.getInstance(AbstractStorageLF.class);

  public static final @NonNls String INDEX_EXTENSION = ".storageRecordIndex";
  public static final @NonNls String DATA_EXTENSION = ".storageData";

  private final Path storagePath;

  protected IRecordsTable recordsTable;
  protected IDataTable dataTable;
  protected StorageLockContext context;
  private final CapacityAllocationPolicy capacityAllocationPolicy;

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

  protected AbstractStorageLF(@NotNull Path storageFilePath) throws IOException {
    this(storageFilePath, SHARED);
  }

  protected AbstractStorageLF(@NotNull Path storageFilePath, @NotNull StorageLockContext context) throws IOException {
    this(storageFilePath, context, CapacityAllocationPolicy.DEFAULT);
  }

  protected AbstractStorageLF(@NotNull Path storageFilePath,
                              CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    this(storageFilePath, SHARED, capacityAllocationPolicy);
  }

  protected AbstractStorageLF(@NotNull Path storageFilePath,
                              @NotNull StorageLockContext context,
                              @Nullable CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    this.storagePath = storageFilePath;
    this.capacityAllocationPolicy = capacityAllocationPolicy != null ? capacityAllocationPolicy
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

    IRecordsTable recordsTable = null;
    IDataTable dataTable;
    try {
      recordsTable = createRecordsTable(context, recordsFile);
      dataTable = createDataTable(context, dataFile);
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

      tryInit(storageFilePath, context, retryCount + 1);
      return;
    }

    this.recordsTable = recordsTable;
    this.dataTable = dataTable;
    this.context = context;

    if (this.dataTable.isCompactNecessary()) {
      compact(storageFilePath);
    }
  }


  private static @NotNull IDataTable createDataTable(@NotNull StorageLockContext context,
                                                     @NotNull Path dataFile) throws IOException {
    return new DataTableLF(dataFile, context);
  }

  protected abstract IRecordsTable createRecordsTable(@NotNull StorageLockContext context, @NotNull Path recordsFile) throws IOException;

  private void compact(@NotNull Path path) {
    withWriteLock(() -> {
      LOG.info("Space waste in " + path + " is " + dataTable.getWaste() + " bytes. Compacting now.");
      long start = System.currentTimeMillis();

      try {
        Path parentDir = path.getParent();
        Path newDataFile = parentDir.resolve(path.getFileName() + ".storageData.backup");
        Files.createDirectories(parentDir);
        createOrTruncateFile(newDataFile);

        Path oldDataFile = parentDir.resolve(path.getFileName() + DATA_EXTENSION);
        IDataTable newDataTable = createDataTable(context, newDataFile);

        RecordIdIterator recordIterator = recordsTable.createRecordIdIterator();
        while (recordIterator.hasNextId()) {
          final int recordId = recordIterator.nextId();
          final long addr = recordsTable.getAddress(recordId);
          final int size = recordsTable.getSize(recordId);

          if (size > 0) {
            assert addr > 0;

            final int capacity = capacityAllocationPolicy.calculateCapacity(size);
            final long newaddr = newDataTable.allocateSpace(capacity);
            final byte[] bytes = new byte[size];
            dataTable.readBytes(addr, bytes);
            newDataTable.writeBytes(newaddr, bytes);
            recordsTable.setAddress(recordId, newaddr);
            recordsTable.setCapacity(recordId, capacity);
          }
        }

        dataTable.close();
        newDataTable.close();

        Files.move(newDataFile, oldDataFile, StandardCopyOption.REPLACE_EXISTING);
        dataTable = createDataTable(context, oldDataFile);
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

  @Override
  public int getVersion() throws IOException {
    return withReadLock(() -> {
      return recordsTable.getVersion();
    });
  }

  @Override
  public void setVersion(int expectedVersion) throws IOException {
    withWriteLock(() -> {
      recordsTable.setVersion(expectedVersion);
    });
  }

  @Override
  public void force() throws IOException {
    withWriteLock(() -> {
      dataTable.force();
      recordsTable.force();
    });
  }

  @Override
  public boolean isDirty() {
    return dataTable.isDirty() || recordsTable.isDirty();
  }

  @Override
  @TestOnly
  public int getLiveRecordsCount() throws IOException {
    return withReadLock(() -> recordsTable.getLiveRecordsCount());
  }

  @Override
  @TestOnly
  public RecordIdIterator createRecordIdIterator() throws IOException {
    return recordsTable.createRecordIdIterator();
  }

  @Override
  public StorageDataOutput writeStream(final int record) {
    return writeStream(record, false);
  }

  @Override
  public StorageDataOutput writeStream(final int record, boolean fixedSize) {
    return new StorageDataOutput(this, record, fixedSize);
  }

  @Override
  public AppenderStream appendStream(int record) {
    return new AppenderStream(record);
  }

  @Override
  public DataInputStream readStream(int record) throws IOException {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new UnsyncByteArrayInputStream(bytes));
  }

  protected byte[] readBytes(int record) throws IOException {
    return withReadLock(() -> {
      final int length = recordsTable.getSize(record);
      if (length == 0 || AbstractRecordsTableLF.isSizeOfRemovedRecord(length)) return ArrayUtilRt.EMPTY_BYTE_ARRAY;
      assert length > 0 : length;

      final long address = recordsTable.getAddress(record);
      byte[] result = new byte[length];
      dataTable.readBytes(address, result);
      return result;
    });
  }

  protected void appendBytes(int record, ByteArraySequence bytes) throws IOException {
    final int delta = bytes.getLength();
    if (delta == 0) return;
    withWriteLock(() -> {
      int capacity = recordsTable.getCapacity(record);
      int oldSize = recordsTable.getSize(record);
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
        long address = recordsTable.getAddress(record) + oldSize;
        dataTable.writeBytes(address, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
        recordsTable.setSize(record, newSize);
      }
    });
  }

  @Override
  public void writeBytes(int record, @NotNull ByteArraySequence bytes, boolean fixedSize) throws IOException {
    withWriteLock(() -> {
      final int requiredLength = bytes.getLength();
      final int currentCapacity = recordsTable.getCapacity(record);

      final int currentSize = recordsTable.getSize(record);
      assert currentSize >= 0;

      if (requiredLength == 0 && currentSize == 0) return;

      final long address;
      if (currentCapacity >= requiredLength) {
        address = recordsTable.getAddress(record);
      }
      else {
        dataTable.reclaimSpace(currentCapacity);

        int newCapacity = fixedSize ? requiredLength : capacityAllocationPolicy.calculateCapacity(requiredLength);
        if (newCapacity < requiredLength) newCapacity = requiredLength;
        address = dataTable.allocateSpace(newCapacity);
        recordsTable.setAddress(record, address);
        recordsTable.setCapacity(record, newCapacity);
      }

      dataTable.writeBytes(address, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
      recordsTable.setSize(record, requiredLength);
    });
  }

  protected void doDeleteRecord(int record) throws IOException {
    dataTable.reclaimSpace(recordsTable.getCapacity(record));
    recordsTable.deleteRecord(record);
  }

  @Override
  public void dispose() {
    withWriteLock(() -> {
      IOUtil.closeSafe(LOG, recordsTable);
      IOUtil.closeSafe(LOG, dataTable);
    });
  }

  @Override
  public void closeAndClean() throws IOException {
    Disposer.dispose(this);
    deleteFiles(storagePath);
  }


  @Override
  public void checkSanity(final int record) throws IOException {
    withReadLock(() -> {
      final int size = recordsTable.getSize(record);
      final int capacity = recordsTable.getCapacity(record);
      final long address = recordsTable.getAddress(record);
      final long dataFileSize = dataTable.getFileSize();
      assert size >= 0 : "[#" + record + "]: size(=" + size + ") must not be negative";
      assert capacity >= 0 : "[#" + record + "]: capacity(=" + capacity + ") -- must NOT be negative";
      assert address >= 0 : "[#" + record + "]: address(=" + address + ") must not be negative";
      assert size <= capacity : "[#" + record + "]: size(=" + size + ") > capacity(=" + capacity + ")";
      assert address + capacity <= dataFileSize
        : "[#" + record + "]: address(=" + address + ")+capacity(=" + size + ") is beyond EOF(=" + dataFileSize + ")";
    });
  }

  @Override
  public void replaceBytes(int record, int offset, @NotNull ByteArraySequence bytes) throws IOException {
    withWriteLock(() -> {
      final int changedBytesLength = bytes.getLength();

      final int currentSize = recordsTable.getSize(record);
      assert currentSize >= 0;
      assert offset + bytes.getLength() <= currentSize;

      if (changedBytesLength == 0) return;

      final long address = recordsTable.getAddress(record);

      dataTable.writeBytes(address + offset, bytes.getInternalBuffer(), bytes.getOffset(), bytes.getLength());
    });
  }

  public static final class StorageDataOutput extends DataOutputStream implements IStorageDataOutput {
    private final AbstractStorageLF myStorage;
    private final int myRecordId;
    private final boolean myFixedSize;

    private StorageDataOutput(AbstractStorageLF storage, int recordId, boolean fixedSize) {
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

    @Override
    public @NotNull ByteArraySequence asByteArraySequence() {
      return getByteStream().asByteArraySequence();
    }
  }

  public final class AppenderStream extends DataOutputStream implements IAppenderStream {
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

    private BufferExposingByteArrayOutputStream getByteStream() {
      return ((BufferExposingByteArrayOutputStream)out);
    }

    @Override
    public @NotNull ByteArraySequence asByteArraySequence() {
      return getByteStream().asByteArraySequence();
    }
  }

  protected <T, E extends Throwable> T withReadLock(@NotNull ThrowableComputable<T, E> runnable) throws E {
    return ConcurrencyUtil.withLock(context.readLock(), runnable);
  }

  protected <E extends Throwable> void withReadLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    ConcurrencyUtil.withLock(context.readLock(), runnable);
  }

  protected <T, E extends Throwable> T withWriteLock(@NotNull ThrowableComputable<T, E> runnable) throws E {
    return ConcurrencyUtil.withLock(context.writeLock(), runnable);
  }

  protected <E extends Throwable> void withWriteLock(@NotNull ThrowableRunnable<E> runnable) throws E {
    ConcurrencyUtil.withLock(context.writeLock(), runnable);
  }
}
