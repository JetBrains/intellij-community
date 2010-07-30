/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RecordDataOutput;
import org.jetbrains.annotations.NonNls;

import java.io.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class AbstractStorage implements Disposable, Forceable {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.storage.Storage");

  @NonNls public static final String INDEX_EXTENSION = ".storageRecordIndex";
  @NonNls public static final String DATA_EXTENSION = ".storageData";

  private static final int MAX_PAGES_TO_FLUSH_AT_A_TIME = 50;

  protected final Object myLock = new Object();

  protected AbstractRecordsTable myRecordsTable;
  protected DataTable myDataTable;
  protected PagePool myPool;

  public static boolean deleteFiles(String storageFilePath) {
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);
    final File dataFile = new File(storageFilePath + DATA_EXTENSION);

    // ensure both files deleted
    final boolean deletedRecordsFile = FileUtil.delete(recordsFile);
    final boolean deletedDataFile = FileUtil.delete(dataFile);
    return deletedRecordsFile && deletedDataFile;
  }

  public static void convertFromOldExtensions(String storageFilePath) {
    FileUtil.delete(new File(storageFilePath + ".rindex"));
    FileUtil.delete(new File(storageFilePath + ".data"));
  }

  protected AbstractStorage(String storageFilePath) throws IOException {
    this(storageFilePath, PagePool.SHARED);
  }

  protected AbstractStorage(String storageFilePath, PagePool pool) throws IOException {
    tryInit(storageFilePath, pool, 0);
  }

  private void tryInit(String storageFilePath, PagePool pool, int retryCount) throws IOException {
    convertFromOldExtensions(storageFilePath);

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
        recordsTable.dispose();
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
    synchronized (myLock) {
      LOG.info("Space waste in " + path + " is " + myDataTable.getWaste() + " bytes. Compacting now.");
      long start = System.currentTimeMillis();

      try {
        File newDataFile = new File(path + ".storageData.backup");
        FileUtil.delete(newDataFile);
        FileUtil.createIfDoesntExist(newDataFile);

        File oldDataFile = new File(path + DATA_EXTENSION);
        DataTable newDataTable = new DataTable(newDataFile, myPool);

        final int count = myRecordsTable.getRecordsCount();
        for (int i = 1; i <= count; i++) {
          final long addr = myRecordsTable.getAddress(i);
          final int size = myRecordsTable.getSize(i);

          if (size > 0) {
            assert addr > 0;

            final int capacity = calcCapacity(size);
            final long newaddr = newDataTable.allocateSpace(capacity);
            final byte[] bytes = new byte[size];
            myDataTable.readBytes(addr, bytes);
            newDataTable.writeBytes(newaddr, bytes);
            myRecordsTable.setAddress(i, newaddr);
            myRecordsTable.setCapacity(i, capacity);
          }
        }

        myDataTable.dispose();
        newDataTable.dispose();

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
    }
  }

  public int getVersion() {
    synchronized (myLock) {
      return myRecordsTable.getVersion();
    }
  }

  public void setVersion(int expectedVersion) {
    synchronized (myLock) {
      myRecordsTable.setVersion(expectedVersion);
    }
  }

  public void force() {
    synchronized (myLock) {
      myDataTable.force();
      myRecordsTable.force();
    }
  }

  public boolean flushSome() {
    synchronized (myLock) {
      boolean okRecords = myRecordsTable.flushSome(MAX_PAGES_TO_FLUSH_AT_A_TIME);
      boolean okData = myDataTable.flushSome(MAX_PAGES_TO_FLUSH_AT_A_TIME);

      return okRecords && okData;
    }
  }

  public boolean isDirty() {
    synchronized (myLock) {
      return myDataTable.isDirty() || myRecordsTable.isDirty();
    }
  }

  private static int calcCapacity(int requiredLength) {
    return Math.max(64, nearestPowerOfTwo(requiredLength * 3 / 2));
  }

  private static int nearestPowerOfTwo(int n) {
    int power = 1;
    while (n != 0) {
      power *= 2;
      n /= 2;
    }
    return power;
  }

  public StorageDataOutput writeStream(final int record) {
    return new StorageDataOutput(this, record);
  }

  public AppenderStream appendStream(int record) {
    return new AppenderStream(record);
  }

  public DataInputStream readStream(int record) throws IOException {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  protected byte[] readBytes(int record) throws IOException {
    synchronized (myLock) {
      final int length = myRecordsTable.getSize(record);
      if (length == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;
      assert length > 0;

      final long address = myRecordsTable.getAddress(record);
      byte[] result = new byte[length];
      myDataTable.readBytes(address, result);

      return result;
    }
  }

  protected void appendBytes(int record, byte[] bytes) throws IOException {
    int delta = bytes.length;
    if (delta == 0) return;

    synchronized (myLock) {
      int capacity = myRecordsTable.getCapacity(record);
      int oldSize = myRecordsTable.getSize(record);
      int newSize = oldSize + delta;
      if (newSize > capacity) {
        if (oldSize > 0) {
          byte[] newbytes = new byte[newSize];
          System.arraycopy(readBytes(record), 0, newbytes, 0, oldSize);
          System.arraycopy(bytes, 0, newbytes, oldSize, delta);
          writeBytes(record, newbytes);
        }
        else {
          writeBytes(record, bytes);
        }
      }
      else {
        long address = myRecordsTable.getAddress(record) + oldSize;
        myDataTable.writeBytes(address, bytes);
        myRecordsTable.setSize(record, newSize);
      }
    }
  }

  protected void writeBytes(int record, byte[] bytes) throws IOException {
    synchronized (myLock) {
      final int requiredLength = bytes.length;
      final int currentCapacity = myRecordsTable.getCapacity(record);

      final int currentSize = myRecordsTable.getSize(record);
      assert currentSize >= 0;

      if (requiredLength == 0 && currentSize == 0) return;

      final long address;
      if (currentCapacity >= requiredLength) {
        address = myRecordsTable.getAddress(record);
      }
      else {
        if (currentCapacity > 0) {
          myDataTable.reclaimSpace(currentCapacity);
        }

        final int newCapacity = calcCapacity(requiredLength);
        address = myDataTable.allocateSpace(newCapacity);
        myRecordsTable.setAddress(record, address);
        myRecordsTable.setCapacity(record, newCapacity);
      }

      myDataTable.writeBytes(address, bytes);
      myRecordsTable.setSize(record, requiredLength);
    }
  }

  protected void doDeleteRecord(int record) throws IOException {
    myDataTable.reclaimSpace(myRecordsTable.getSize(record));
    myRecordsTable.deleteRecord(record);
  }

  public void dispose() {
    synchronized (myLock) {
      force();
      myRecordsTable.dispose();
      myDataTable.dispose();
    }
  }

  public void checkSanity(final int record) {
    synchronized (myLock) {
      final int size = myRecordsTable.getSize(record);
      assert size >= 0;
      final long address = myRecordsTable.getAddress(record);
      assert address >= 0;
      assert address + size < myDataTable.getFileSize();
    }
  }

  public static class StorageDataOutput extends DataOutputStream implements RecordDataOutput {
    private final AbstractStorage myStorage;
    private final int myRecordId;

    public StorageDataOutput(AbstractStorage storage, int recordId) {
      this(storage, recordId, new ByteArrayOutputStream());
    }

    protected StorageDataOutput(AbstractStorage storage, int recordId, OutputStream stream) {
      super(stream);
      myStorage = storage;
      myRecordId = recordId;
    }

    public void close() throws IOException {
      super.close();
      myStorage.writeBytes(myRecordId, getByteStream().toByteArray());
    }

    protected ByteArrayOutputStream getByteStream() {
      return ((ByteArrayOutputStream)out);
    }

    public int getRecordId() {
      return myRecordId;
    }
  }

  public class AppenderStream extends DataOutputStream {
    private final int myRecordId;

    public AppenderStream(int recordId) {
      super(new ByteArrayOutputStream());
      myRecordId = recordId;
    }

    public void close() throws IOException {
      super.close();
      appendBytes(myRecordId, ((ByteArrayOutputStream)out).toByteArray());
    }
  }
}
