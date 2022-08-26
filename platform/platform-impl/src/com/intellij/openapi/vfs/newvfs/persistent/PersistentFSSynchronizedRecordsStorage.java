// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
final class PersistentFSSynchronizedRecordsStorage extends PersistentFSRecordsStorage {
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

  private <V, E extends Throwable> V read(ThrowableComputable<V, E> action) throws E {
    myFile.getStorageLockContext().lockRead();
    try {
      return action.compute();
    }
    finally {
      myFile.getStorageLockContext().unlockRead();
    }
  }

  private <E extends Throwable> void write(ThrowableRunnable<E> action) throws E {
    myFile.getStorageLockContext().lockWrite();
    try {
      action.run();
    }
    finally {
      myFile.getStorageLockContext().unlockWrite();
    }
  }

  private <V, E extends Throwable> V write(ThrowableComputable<V, E> action) throws E {
    myFile.getStorageLockContext().lockWrite();
    try {
      return action.compute();
    }
    finally {
      myFile.getStorageLockContext().unlockWrite();
    }
  }

  @NotNull
  private final ResizeableMappedFile myFile;
  private final ByteBuffer myPooledWriteBuffer = ByteBuffer.allocateDirect(RECORD_SIZE);
  @NotNull
  private final AtomicInteger myGlobalModCount;
  @NotNull
  private final AtomicInteger myRecordCount;

  PersistentFSSynchronizedRecordsStorage(@NotNull ResizeableMappedFile file) throws IOException {
    myFile = file;
    if (myFile.isNativeBytesOrder()) myPooledWriteBuffer.order(ByteOrder.nativeOrder());
    myGlobalModCount = new AtomicInteger(readGlobalModCount());
    myRecordCount = new AtomicInteger((int)(length() / RECORD_SIZE));
  }

  @Override
  public int getGlobalModCount() {
    return myGlobalModCount.get();
  }

  private int readGlobalModCount() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET);
    });
  }

  private void saveGlobalModCount() throws IOException {
    write(() -> {
      myFile.putInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET, getGlobalModCount());
    });
  }

  @Override
  public int incGlobalModCount() {
    return myGlobalModCount.incrementAndGet();
  }

  @Override
  public long getTimestamp() throws IOException {
    return read(() -> {
      return myFile.getLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET);
    });
  }

  @Override
  public void setVersion(int version) throws IOException {
    write(() -> {
      myFile.putInt(PersistentFSHeaders.HEADER_VERSION_OFFSET, version);
      myFile.putLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
    });
  }

  @Override
  public int getVersion() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_VERSION_OFFSET);
    });
  }

  @Override
  public void setConnectionStatus(int connectionStatus) throws IOException {
    write(() -> {
      myFile.putInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
    });
  }

  @Override
  public int getConnectionStatus() throws IOException {
    return read(() -> {
      return myFile.getInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET);
    });
  }

  @Override
  public int getNameId(int id) throws IOException {
    assert id > 0 : id;
    return getRecordInt(id, NAME_OFFSET);
  }

  @Override
  public void setNameId(int id, int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    putRecordInt(id, NAME_OFFSET, nameId);
  }

  @Override
  public int getParent(int id) throws IOException {
    return getRecordInt(id, PARENT_OFFSET);
  }

  @Override
  public void setParent(int id, int parent) throws IOException {
    putRecordInt(id, PARENT_OFFSET, parent);
  }

  @Override
  public int getModCount(int id) throws IOException {
    return getRecordInt(id, MOD_COUNT_OFFSET);
  }

  @Override
  @PersistentFS.Attributes
  public int getFlags(int id) throws IOException {
    return getRecordInt(id, FLAGS_OFFSET);
  }

  @Override
  public boolean setFlags(int id, @PersistentFS.Attributes int flags) throws IOException {
    return write(() -> {
      final boolean reallyChanged = getFlags(id) != flags;
      if (reallyChanged) {
        putRecordInt(id, FLAGS_OFFSET, flags);
      }
      return reallyChanged;
    });
  }

  @Override
  public void setModCount(int id, int value) throws IOException {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  @Override
  public int getContentRecordId(int fileId) throws IOException {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  @Override
  public void setContentRecordId(int id, int value) throws IOException {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  @Override
  public int getAttributeRecordId(int id) throws IOException {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  @Override
  public void setAttributeRecordId(int id, int value) throws IOException {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  @Override
  public long getTimestamp(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, TIMESTAMP_OFFSET));
    });
  }

  @Override
  public boolean putTimestamp(int id, long value) throws IOException {
    return write(() -> {
      int timeStampOffset = getOffset(id, TIMESTAMP_OFFSET);
      final boolean reallyChanged = myFile.getLong(timeStampOffset) != value;
      if (reallyChanged) {
        myFile.putLong(timeStampOffset, value);
      }
      return reallyChanged;
    });
  }

  @Override
  public long getLength(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, LENGTH_OFFSET));
    });
  }

  @Override
  public boolean putLength(int id, long value) throws IOException {
    return write(() -> {
      int lengthOffset = getOffset(id, LENGTH_OFFSET);
      final boolean reallyChanged = myFile.getLong(lengthOffset) != value;
      if (reallyChanged) {
        myFile.putLong(lengthOffset, value);
      }
      return reallyChanged;
    });
  }

  @Override
  public void cleanRecord(int id) throws IOException {
    write(() -> {
      myRecordCount.updateAndGet(operand -> Math.max(id + 1, operand));
      myFile.put(((long)id) * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    });
  }

  @Override
  public int allocateRecord() {
    return myRecordCount.getAndIncrement();
  }

  private int getRecordInt(int id, int offset) throws IOException {
    final int absoluteOffset = getOffset(id, offset);
    return read(() -> {
      return myFile.getInt(absoluteOffset);
    });
  }

  private void putRecordInt(int id, int offset, int value) throws IOException {
    final int absoluteOffset = getOffset(id, offset);
    write(() -> {
      myFile.putInt(absoluteOffset, value);
    });
  }

  @Override
  public void setAttributesAndIncModCount(int id, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteMissed)
    throws IOException {
    write(() -> {
      //FIXME RC: method name setAttributesAndIncModCount, but there is no modCount increment here!
      assert myPooledWriteBuffer.position() == 0;
      myPooledWriteBuffer.putLong(TIMESTAMP_OFFSET, timestamp);
      myPooledWriteBuffer.putInt(ATTR_REF_OFFSET, overwriteMissed ? 0 : getAttributeRecordId(id));
      myPooledWriteBuffer.putLong(LENGTH_OFFSET, length);
      myPooledWriteBuffer.putInt(FLAGS_OFFSET, flags);
      myPooledWriteBuffer.putInt(NAME_OFFSET, nameId);
      myPooledWriteBuffer.putInt(PARENT_OFFSET, parentId);
      assert myPooledWriteBuffer.position() == 0;
      myFile.put(((long)id) * RECORD_SIZE, myPooledWriteBuffer);
      myPooledWriteBuffer.rewind();
    });
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  @Override
  public long length() {
    return read(() -> {
      return myFile.length();
    });
  }

  @Override
  public void close() throws IOException {
    write(() -> {
      saveGlobalModCount();
      myFile.close();
    });
  }

  @Override
  public void force() throws IOException {
    write(() -> {
      saveGlobalModCount();
      myFile.force();
    });
  }

  @Override
  public boolean isDirty() {
    return myFile.isDirty();
  }

  @Override
  public boolean processAllRecords(@NotNull PersistentFSRecordsStorage.FsRecordProcessor operator) throws IOException {
    return read(() -> {
      myFile.force();
      return myFile.readChannel(ch -> {
        ByteBuffer buffer = ByteBuffer.allocateDirect(RECORD_SIZE * 1024);
        if (myFile.isNativeBytesOrder()) buffer.order(ByteOrder.nativeOrder());
        try {
          int id = 1, limit, offset;
          while ((limit = ch.read(buffer)) >= RECORD_SIZE) {
            offset = id == 1 ? RECORD_SIZE : 0; // skip header
            for (; offset < limit; offset += RECORD_SIZE) {
              int nameId = buffer.getInt(offset + NAME_OFFSET);
              int flags = buffer.getInt(offset + FLAGS_OFFSET);
              int parentId = buffer.getInt(offset + PARENT_OFFSET);
              operator.process(id, nameId, flags, parentId, false);
              id++;
            }
            buffer.position(0);
          }
        }
        catch (IOException ignore) {
        }
        return true;
      });
    });
  }
}
