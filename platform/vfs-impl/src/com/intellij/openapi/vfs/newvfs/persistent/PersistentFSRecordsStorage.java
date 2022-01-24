// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Processor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PersistentFSRecordsStorage {
  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = 4;
  private static final int CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_SIZE = 4;
  private static final int TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MOD_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MOD_COUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int LENGTH_SIZE = 8;

  static final int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;
  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private final ReadWriteLock myLock = new ReentrantReadWriteLock();

  private <V, E extends Throwable> V read(ThrowableComputable<V, E> action) throws E {
    myLock.readLock().lock();
    try {
      return action.compute();
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  private <V, E extends Throwable> V write(ThrowableComputable<V, E> action) throws E {
    myLock.writeLock().lock();
    try {
      return action.compute();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  @NotNull
  private final ResizeableMappedFile myFile;

  public PersistentFSRecordsStorage(@NotNull ResizeableMappedFile file) {
    myFile = file;
  }

  int getGlobalModCount() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET);
    });
  }

  int incGlobalModCount() throws IOException {
    return write(() -> {
      final int count = getGlobalModCount() + 1;
      myFile.putInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET, count);
      return count;
    });
  }

  long getTimestamp() throws IOException {
    return read(() -> {
      return myFile.getLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET);
    });
  }

  void setVersion(int version) throws IOException {
    write(() -> {
      myFile.putInt(PersistentFSHeaders.HEADER_VERSION_OFFSET, version);
      myFile.putLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
      return null;
    });
  }

  int getVersion() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_VERSION_OFFSET);
    });
  }

  void setConnectionStatus(int connectionStatus) throws IOException {
    write(() -> {
      myFile.putInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
      return null;
    });
  }

  int getConnectionStatus() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET);
    });
  }

  int getNameId(int id) throws IOException {
    return read(() -> {
      assert id > 0 : id;
      return getRecordInt(id, NAME_OFFSET);
    });
  }

  void setNameId(int id, int nameId) throws IOException {
    write(() -> {
      PersistentFSConnection.ensureIdIsValid(nameId);
      putRecordInt(id, NAME_OFFSET, nameId);
      return null;
    });
  }

  int getParent(int id) throws IOException {
    return read(() -> {
      return getRecordInt(id, PARENT_OFFSET);
    });
  }

  void setParent(int id, int parent) throws IOException {
    write(() -> {
      putRecordInt(id, PARENT_OFFSET, parent);
      return null;
    });
  }

  int getModCount(int id) throws IOException {
    return read(() -> {
      return getRecordInt(id, MOD_COUNT_OFFSET);
    });
  }

  @PersistentFS.Attributes
  int doGetFlags(int id) throws IOException {
    return read(() -> {
      return getRecordInt(id, FLAGS_OFFSET);
    });
  }

  void setFlags(int id, @PersistentFS.Attributes int flags) throws IOException {
    write(() -> {
      putRecordInt(id, FLAGS_OFFSET, flags);
      return null;
    });
  }

  void setModCount(int id, int value) throws IOException {
    write(() -> {
      putRecordInt(id, MOD_COUNT_OFFSET, value);
      return null;
    });
  }

  int getContentRecordId(int fileId) throws IOException {
    return read(() -> {
      return getRecordInt(fileId, CONTENT_OFFSET);
    });
  }

  void setContentRecordId(int id, int value) throws IOException {
    write(() -> {
      putRecordInt(id, CONTENT_OFFSET, value);
      return null;
    });
  }

  int getAttributeRecordId(int id) throws IOException {
    return read(() -> {
      return getRecordInt(id, ATTR_REF_OFFSET);
    });
  }

  void setAttributeRecordId(int id, int value) throws IOException {
    write(() -> {
      putRecordInt(id, ATTR_REF_OFFSET, value);
      return null;
    });
  }

  long getTimestamp(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, TIMESTAMP_OFFSET));
    });
  }

  boolean putTimeStamp(int id, long value) throws IOException {
    return write(() -> {
      int timeStampOffset = getOffset(id, TIMESTAMP_OFFSET);
      if (myFile.getLong(timeStampOffset) != value) {
        myFile.putLong(timeStampOffset, value);
        return true;
      }
      return false;
    });
  }

  long getLength(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, LENGTH_OFFSET));
    });
  }

  boolean putLength(int id, long value) throws IOException {
    return write(() -> {
      int lengthOffset = getOffset(id, LENGTH_OFFSET);
      if (myFile.getLong(lengthOffset) != value) {
        myFile.putLong(lengthOffset, value);
        return true;
      }
      return false;
    });
  }

  void cleanRecord(int id) throws IOException {
    write(() -> {
      myFile.put(((long)id) * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
      return null;
    });
  }

  private int getRecordInt(int id, int offset) throws IOException {
    return read(() -> {
      return myFile.getInt(getOffset(id, offset));
    });
  }

  private void putRecordInt(int id, int offset, int value) throws IOException {
    write(() -> {
      myFile.putInt(getOffset(id, offset), value);
      return null;
    });
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  long length() {
    return read(() -> {
      return myFile.length();
    });
  }

  void close() {
    write(() -> {
      try {
        myFile.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return null;
    });
  }

  void force() {
    write(() -> {
      try {
        myFile.force();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return null;
    });
  }

  boolean isDirty() {
    return myFile.isDirty();
  }

  boolean processAll(@NotNull Processor<? super Record> processor) throws IOException {
    Record r = new Record();
    byte[] bytes = new byte[RECORD_SIZE];
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    if (IOUtil.useNativeByteOrderForByteBuffers()) buffer.order(ByteOrder.nativeOrder());
    return read(() -> {
      ProgressManager.checkCanceled();
      myFile.force();
      return myFile.readInputStream(is -> {
        try (BufferedInputStream bis = new BufferedInputStream(is)) {
          if (bis.read(bytes) != bytes.length) return true; // header
          int id = 1;
          while (bis.read(bytes) == bytes.length) {
            if (id % 100 == 0) {
              ProgressManager.checkCanceled();
            }
            r.id = id++;
            buffer.position(0);
            r.parent = buffer.getInt();
            r.name = buffer.getInt();
            r.flags = buffer.getInt();
            r.attr_ref = buffer.getInt();
            r.content = buffer.getInt();
            r.timestamp = buffer.getLong();
            r.mod_count = buffer.getInt();
            r.length = buffer.getLong();
            if (!processor.process(r)) return false;
          }
        }
        catch (IOException ignore) {
        }
        return true;
      });
    });
  }

  static final class Record {
    int id, parent, name, flags, attr_ref, content, mod_count;
    long timestamp, length;
  }
}
