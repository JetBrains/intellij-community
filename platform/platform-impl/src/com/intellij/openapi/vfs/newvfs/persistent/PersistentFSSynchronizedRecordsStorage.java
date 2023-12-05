// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.HEADER_ERRORS_ACCUMULATED_OFFSET;

@ApiStatus.Internal
final class PersistentFSSynchronizedRecordsStorage implements PersistentFSRecordsStorage {
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

  static {
    //We use 0-th record for header fields, so record size should be big enough for header

    //noinspection ConstantConditions
    assert PersistentFSHeaders.HEADER_SIZE <= RECORD_SIZE :
      "sizeof(storage.header)(=" + PersistentFSHeaders.HEADER_SIZE + ") > RECORD_SIZE(=" + RECORD_SIZE + ")";
  }


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

  private final @NotNull ResizeableMappedFile myFile;
  private final ByteBuffer myPooledWriteBuffer = ByteBuffer.allocateDirect(RECORD_SIZE);
  private final @NotNull AtomicInteger myGlobalModCount;
  private final @NotNull AtomicInteger myRecordCount;

  PersistentFSSynchronizedRecordsStorage(@NotNull ResizeableMappedFile file) throws IOException {
    myFile = file;
    if (myFile.isNativeBytesOrder()) myPooledWriteBuffer.order(ByteOrder.nativeOrder());
    myGlobalModCount = new AtomicInteger(readGlobalModCount());
    final long length = file.length();

    final int recordsCount;
    if (length <= RECORD_SIZE) {
      recordsCount = 0;
    }
    else {
      recordsCount = (int)(length / RECORD_SIZE - 1);//first RECORD_SIZE bytes used as a storage header
    }
    myRecordCount = new AtomicInteger(recordsCount);
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
      //TODO RC: incGlobalModCount?
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
  public int getErrorsAccumulated() throws IOException {
    return read(() -> myFile.getInt(HEADER_ERRORS_ACCUMULATED_OFFSET));
  }

  @Override
  public void setErrorsAccumulated(int errors) throws IOException {
    write(() -> {
      myFile.putInt(HEADER_ERRORS_ACCUMULATED_OFFSET, errors);
    });
  }

  @Override
  public int getNameId(int id) throws IOException {
    assert id > 0 : id;
    return getRecordInt(id, NAME_OFFSET);
  }

  @Override
  public int updateNameId(int id, int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    //FIXME RC: fake thread-unsafe implementation -- whole class is outdated and to be removed
    int previousNameId = getNameId(id);
    putRecordInt(id, NAME_OFFSET, nameId);
    return previousNameId;
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
  public @PersistentFS.Attributes int getFlags(int id) throws IOException {
    return getRecordInt(id, FLAGS_OFFSET);
  }

  @Override
  public boolean setFlags(int id, @PersistentFS.Attributes int newFlags) throws IOException {
    return putRecordInt(id, FLAGS_OFFSET, newFlags);
  }

  @Override
  public void markRecordAsModified(int recordId) throws IOException {
    final int modCount = incrementGlobalModCount();
    final long absoluteOffset = getOffset(recordId, MOD_COUNT_OFFSET);
    write(() -> {
      myFile.putInt(absoluteOffset, modCount);
    });
  }

  @Override
  public int getContentRecordId(int fileId) throws IOException {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  @Override
  public boolean setContentRecordId(int id, int contentRef) throws IOException {
    return putRecordInt(id, CONTENT_OFFSET, contentRef);
  }

  @Override
  public int getAttributeRecordId(int id) throws IOException {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  @Override
  public void setAttributeRecordId(int id, int value) throws IOException {
    if (value < NULL_ID) {
      throw new IllegalArgumentException("file[id: " + id + "].attributeRecordId(=" + value + ") must be >=0");
    }
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  @Override
  public long getTimestamp(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, TIMESTAMP_OFFSET));
    });
  }

  @Override
  public boolean setTimestamp(int id, long value) throws IOException {
    return putRecordLong(id, TIMESTAMP_OFFSET, value);
  }

  @Override
  public long getLength(int id) throws IOException {
    return read(() -> {
      return myFile.getLong(getOffset(id, LENGTH_OFFSET));
    });
  }

  @Override
  public boolean setLength(int id, long value) throws IOException {
    return putRecordLong(id, LENGTH_OFFSET, value);
  }

  @Override
  public void cleanRecord(int recordId) throws IOException {
    checkIdIsValid(recordId);
    write(() -> {
      final long absoluteRecordOffset = getOffset(recordId, 0);
      myFile.put(absoluteRecordOffset, ZEROES, 0, RECORD_SIZE);
      incrementGlobalModCount();
    });
  }

  @Override
  public int allocateRecord() {
    incrementGlobalModCount();
    return myRecordCount.incrementAndGet();
  }

  private int getRecordInt(int id, int offset) throws IOException {
    final long absoluteOffset = getOffset(id, offset);
    return read(() -> {
      return myFile.getInt(absoluteOffset);
    });
  }

  private boolean putRecordInt(int recordId,
                               int relativeOffset,
                               int value) throws IOException {
    final long absoluteOffset = getOffset(recordId, relativeOffset);
    final long absoluteOffsetModCount = getOffset(recordId, MOD_COUNT_OFFSET);
    return write(() -> {
      final boolean reallyChanged = myFile.getInt(absoluteOffset) != value;
      if (reallyChanged) {
        final int modCount = incrementGlobalModCount();
        myFile.putInt(absoluteOffset, value);
        myFile.putInt(absoluteOffsetModCount, modCount);
      }
      return reallyChanged;
    });
  }

  private Boolean putRecordLong(final int recordId,
                                final int relativeFieldOffset,
                                final long newValue) throws IOException {
    final long absoluteFieldOffset = getOffset(recordId, relativeFieldOffset);
    final long absoluteModCountOffset = getOffset(recordId, MOD_COUNT_OFFSET);
    return write(() -> {
      final boolean reallyChanged = myFile.getLong(absoluteFieldOffset) != newValue;
      if (reallyChanged) {
        final int modCount = incrementGlobalModCount();
        myFile.putLong(absoluteFieldOffset, newValue);
        myFile.putInt(absoluteModCountOffset, modCount);
      }
      return reallyChanged;
    });
  }

  private int incrementGlobalModCount() {
    return myGlobalModCount.incrementAndGet();
  }

  @Override
  public void fillRecord(int id, long timestamp, long length, int flags, int nameId, int parentId, boolean overwriteAttrRef)
    throws IOException {
    write(() -> {
      assert myPooledWriteBuffer.position() == 0;
      final int modCount = incrementGlobalModCount();
      myPooledWriteBuffer.putInt(PARENT_OFFSET, parentId);
      myPooledWriteBuffer.putInt(NAME_OFFSET, nameId);
      myPooledWriteBuffer.putInt(FLAGS_OFFSET, flags);
      myPooledWriteBuffer.putInt(ATTR_REF_OFFSET, overwriteAttrRef ? 0 : getAttributeRecordId(id));
      myPooledWriteBuffer.putLong(TIMESTAMP_OFFSET, timestamp);
      myPooledWriteBuffer.putLong(MOD_COUNT_OFFSET, modCount);
      myPooledWriteBuffer.putLong(LENGTH_OFFSET, length);
      assert myPooledWriteBuffer.position() == 0;
      myFile.put(((long)id) * RECORD_SIZE, myPooledWriteBuffer);
      myPooledWriteBuffer.rewind();
    });
  }

  private long getOffset(final int id,
                         final int fieldOffset) {
    checkIdIsValid(id);
    final long absoluteFileOffset = id * (long)RECORD_SIZE + fieldOffset;
    assert absoluteFileOffset >= 0 : "offset(" + id + ", " + fieldOffset + ") = " + absoluteFileOffset + " must be >=0";
    return absoluteFileOffset;
  }

  @Override
  public int recordsCount() {
    return myRecordCount.get();
  }

  @Override
  public int maxAllocatedID() {
    return myRecordCount.get();
  }

  @Override
  public void close() throws IOException {
    write(() -> {
      saveGlobalModCount();
      //Newly allocated record could be not yet modified -- and myFile automatically expands only
      // on modification. To not lose recordCount value -- expand file manually.
      int recordsCount = myRecordCount.get();
      if (recordsCount == 0) {
        myFile.setLogicalSize(PersistentFSHeaders.HEADER_SIZE);
      }
      else {
        int fileSize = (recordsCount + 1) * RECORD_SIZE;
        myFile.setLogicalSize(fileSize);
      }
      myFile.close();
    });
  }

  @Override
  public void closeAndClean() throws IOException {
    myFile.closeAndRemoveAllFiles();
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
      final long writtenRecordsLength = myFile.length();//could be different than actual file length
      return myFile.readChannel(ch -> {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(RECORD_SIZE * 1024);
        if (myFile.isNativeBytesOrder()) {
          buffer.order(ByteOrder.nativeOrder());
        }
        try {
          long positionInFile = RECORD_SIZE;
          int recordId = MIN_VALID_ID;
          int limit;
          while ((limit = ch.read(buffer)) >= RECORD_SIZE) {
            int offsetInBuffer = (recordId == MIN_VALID_ID) ? RECORD_SIZE : 0; // skip header
            for (;
                 offsetInBuffer < limit && positionInFile < writtenRecordsLength;
                 offsetInBuffer += RECORD_SIZE) {
              final int nameId = buffer.getInt(offsetInBuffer + NAME_OFFSET);
              final int flags = buffer.getInt(offsetInBuffer + FLAGS_OFFSET);
              final int parentId = buffer.getInt(offsetInBuffer + PARENT_OFFSET);
              final int attributeRecordId = buffer.getInt(offsetInBuffer + ATTR_REF_OFFSET);
              final int contentId = buffer.getInt(offsetInBuffer + CONTENT_OFFSET);

              operator.process(recordId, nameId, flags, parentId, attributeRecordId, contentId, /*corrupted: */ false);

              recordId++;
              positionInFile += RECORD_SIZE;
            }
            buffer.clear();
          }
        }
        catch (IOException ignore) {
        }
        return true;
      });
    });
  }

  private void checkIdIsValid(final int recordId) {
    final int allocatedSoFar = myRecordCount.get();
    if (!(FSRecords.NULL_FILE_ID < recordId && recordId <= allocatedSoFar)) {
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + allocatedSoFar + "]");
    }
  }
}
