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
package com.intellij.util.io.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RandomAccessDataFile;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class CompactStorage extends AbstractStorage {

  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_VERSION_OFFSET = 4;
  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int VERSION = 3;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f + VERSION;

  private static final int SIZE_OFFSET = 0;
  private static final int CAPACITY_OFFSET = SIZE_OFFSET + 4;
  private static final int DATA_OFFSET = CAPACITY_OFFSET + 4;
  private static final byte[] ZEROES = new byte[DATA_OFFSET];


  private final RandomAccessDataFile myData;

  private boolean myIsDirty;
  private final Object lock = new Object();
  private static final int MAX_PAGES_TO_FLUSH_AT_A_TIME = 50;
  @NonNls private static final String DATA_EXTENSION = ".compact";
  @NonNls private static final String INDEX_EXTENSION = ".freeRecords";

  public static boolean deleteFiles(String storageFilePath) {
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);
    final File dataFile = new File(storageFilePath + DATA_EXTENSION);

    // ensure both files deleted
    final boolean deletedRecordsFile = FileUtil.delete(recordsFile);
    final boolean deletedDataFile = FileUtil.delete(dataFile);
    return deletedRecordsFile && deletedDataFile;
  }


  public CompactStorage(String storageFilePath) throws IOException {

    Storage.convertFromOldExtensions(storageFilePath);
    FileUtil.delete(new File(storageFilePath + Storage.INDEX_EXTENSION));
    FileUtil.delete(new File(storageFilePath + Storage.DATA_EXTENSION));

    final File dataFile = new File(storageFilePath + DATA_EXTENSION);
    final File recordsFile = new File(storageFilePath + INDEX_EXTENSION);

    FileUtil.createIfDoesntExist(recordsFile);
    FileUtil.createIfDoesntExist(dataFile);

    myData = new RandomAccessDataFile(dataFile, PagePool.SHARED);
    if (myData.length() == 0) {
      myData.putInt(HEADER_MAGIC_OFFSET, CONNECTED_MAGIC);
      myData.putInt(HEADER_VERSION_OFFSET, 0);
      myIsDirty = true;
    } else {
      readInHeader(dataFile);
    }
  }

  private void readInHeader(File filePath) throws IOException {
    int magic = myData.getInt(HEADER_MAGIC_OFFSET);
    if (magic != SAFELY_CLOSED_MAGIC) {
      myData.dispose();
      throw new IOException("Records table for '" + filePath + "' haven't been closed correctly. Rebuild required.");
    }
  }


  private void cleanRecord(int record) {
    myData.put(record, ZEROES, 0, DATA_OFFSET);
  }


  public boolean isDirty() {
    synchronized (lock) {
      return myData.isDirty();
    }
  }

  public void dispose() {
    markClean();
    myData.dispose();
  }

  public void force() {
    markClean();
    myData.force();
  }

  public void markDirty() {
    if (!myIsDirty) {
      myIsDirty = true;
      myData.putInt(HEADER_MAGIC_OFFSET, CONNECTED_MAGIC);
    }
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = false;
      myData.putInt(HEADER_MAGIC_OFFSET, SAFELY_CLOSED_MAGIC);
    }
  }

  public int createNewRecord(int requiredLength) {
    markDirty();
    int record = (int)myData.length();
    setSize(record, 0);
    int capacity = requiredLength;

    setCapacity(record, capacity);
    myData.put(record + DATA_OFFSET + capacity - 1, new byte[] {0}, 0, 1);
    return record;
  }

  public void deleteRecord(int record) {

  }

  public byte[] readBytes(int record) {
    synchronized (lock) {
      int size = getSize(record);
      assert size >= 0 : "Record: " + record;
      byte[] bytes = new byte[size];
      myData.get(record + DATA_OFFSET, bytes, 0, size);
      return bytes;
    }
  }

  public void checkSanity(int record) {
    //TODO
  }

  public boolean flushSome() {
    synchronized (lock) {
      myData.flushSomePages(MAX_PAGES_TO_FLUSH_AT_A_TIME);
      if (!myData.isDirty()) {
        force();
        return true;
      }
      return false;

    }
  }

  public int getVersion() {
    synchronized (lock) {
      return myData.getInt(HEADER_VERSION_OFFSET);
    }
  }

  public void setVersion(int expectedVersion) {
    synchronized (lock) {
      myData.putInt(HEADER_VERSION_OFFSET, expectedVersion);
    }
  }

  protected int appendBytes(int record, byte[] bytes) {
    assert record > 0;

    int delta = bytes.length;
    if (delta == 0) return record;

    synchronized (lock) {
      int capacity = getCapacity(record);
      int oldSize = getSize(record);
      int newSize = oldSize + delta;
      if (newSize > capacity) {
        if (oldSize > 0) {
          byte[] newbytes = new byte[newSize];
          System.arraycopy(readBytes(record), 0, newbytes, 0, oldSize);
          System.arraycopy(bytes, 0, newbytes, oldSize, delta);
          int newRecord = createNewRecord(0);
          doWriteBytes(newRecord, newbytes, capacity, oldSize);
          return newRecord;
        }
        else {
          int newRecord = createNewRecord(0);
          doWriteBytes(newRecord, bytes, capacity, oldSize);
          return newRecord;
        }
      }
      else {
        myData.put(record + DATA_OFFSET + oldSize, bytes, 0, bytes.length);
        setSize(record, newSize);
      }
    }
    return record;
  }

  public void writeBytes(int record, byte[] bytes) {

    assert record > 0;

    synchronized (lock) {
      int currentCapacity = getCapacity(record);

      int currentSize = getSize(record);
      assert currentSize >= 0;

      doWriteBytes(record, bytes, currentCapacity, currentSize);
    }

  }

  public int ensureCapacity(int record, int capacity) {
    int oldCapacity = getCapacity(record);
    if (oldCapacity < capacity) {
      return createNewRecord(0);
    }
    return record;
  }

  private void doWriteBytes(int record, byte[] bytes, int currentCapacity, int currentSize) {
    final int requiredLength = bytes.length;
    if (requiredLength == 0 && currentSize == 0) return;

    assert currentCapacity >= requiredLength;

    setSize(record, requiredLength);
    myData.put(record + DATA_OFFSET, bytes, 0, bytes.length);
  }

  private void setCapacity(int record, int newCapacity) {
    myData.putInt(record + CAPACITY_OFFSET, newCapacity);
  }

  private int getCapacity(int record) {
    return myData.getInt(record + CAPACITY_OFFSET);
  }

  private int getSize(int record) {
    return myData.getInt(record + SIZE_OFFSET);
  }

  private void setSize(int record, int requiredLength) {
    myData.putInt(record + SIZE_OFFSET, requiredLength);
  }

  public void replaceIntInData(int attrsRecord, int recordId, int oldInt) {
    int old = myData.getInt(attrsRecord + DATA_OFFSET);
    if (old != oldInt) {
      System.out.println("wtf");
    }
    myData.putInt(attrsRecord + DATA_OFFSET, recordId);
  }
}
