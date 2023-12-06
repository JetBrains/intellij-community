// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PagedStorage;
import com.intellij.util.io.pagecache.PagedStorageWithPageUnalignedAccess;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.pagecache.impl.PageContentLockingStrategy.SharedLockLockingStrategy;

/**
 * {@link AppendableObjectStorage} implementation on the top of {@link PagedStorage}
 * valueId == offset of value in a file
 */
//@NotThreadSafe
public final class AppendableStorageBackedByPagedStorageLockFree<Data> implements AppendableObjectStorage<Data> {

  @VisibleForTesting
  @ApiStatus.Internal
  public static final int APPEND_BUFFER_SIZE = 4096;

  private static final ThreadLocal<DataStreamOverPagedStorage> TLOCAL_READ_STREAMS =
    ThreadLocal.withInitial(() -> new DataStreamOverPagedStorage());

  private final PagedStorage storage;
  private final ReentrantReadWriteLock storageLock;

  private int fileLength;
  private @Nullable AppendMemoryBuffer appendBuffer;

  private final @NotNull DataExternalizer<Data> dataDescriptor;


  public AppendableStorageBackedByPagedStorageLockFree(@NotNull Path file,
                                                       @Nullable StorageLockContext lockContext,
                                                       int pageSize,
                                                       boolean valuesAreBufferAligned,
                                                       @NotNull DataExternalizer<Data> dataDescriptor,
                                                       @NotNull ReentrantReadWriteLock storageLock) throws IOException {
    this.storageLock = storageLock;
    PagedFileStorageWithRWLockedPageContent storage = PagedFileStorageWithRWLockedPageContent.createWithDefaults(
      file,
      lockContext,
      pageSize,
      /*nativeByteOrder: */false, //TODO RC: why not native order?
      new SharedLockLockingStrategy(this.storageLock)
    );
    this.storage = valuesAreBufferAligned ? storage : new PagedStorageWithPageUnalignedAccess(storage);
    this.dataDescriptor = dataDescriptor;
    fileLength = Math.toIntExact(this.storage.length());
  }

  @Override
  public void clear() throws IOException {
    storage.clear();
    fileLength = 0;
  }

  @Override
  public void lockRead() {
    storageLock.readLock().lock();
  }

  @Override
  public void unlockRead() {
    storageLock.readLock().unlock();
  }

  @Override
  public void lockWrite() {
    storageLock.writeLock().lock();
  }

  @Override
  public void unlockWrite() {
    storageLock.writeLock().unlock();
  }

  @Override
  public boolean isDirty() {
    return AppendMemoryBuffer.hasChanges(appendBuffer) || storage.isDirty();
  }

  private void flushAppendBuffer() throws IOException {
    if (AppendMemoryBuffer.hasChanges(appendBuffer)) {
      int bufferPosition = appendBuffer.getBufferPosition();
      storage.put(fileLength, appendBuffer.getAppendBuffer(), 0, bufferPosition);
      fileLength += bufferPosition;
      appendBuffer = appendBuffer.rewind(fileLength);
    }
  }

  @Override
  public void force() throws IOException {
    flushAppendBuffer();
    storage.force();
  }

  @Override
  public void close() throws IOException {
    try {
      ExceptionUtil.runAllAndRethrowAllExceptions(
        IOException.class,
        () -> new IOException("Failed to .close() appendable storage [" + storage.getFile() + "]"),
        this::flushAppendBuffer,
        storage::close
      );
    }
    finally {
      TLOCAL_READ_STREAMS.remove();
    }
  }

  @Override
  public Data read(int valueId, boolean checkAccess) throws IOException {
    AppendMemoryBuffer buffer = appendBuffer;
    int offset = valueId;
    if (buffer != null
        && (long)offset >= buffer.startingOffsetInFile) {
      AppendMemoryBuffer copyForRead = buffer.copy();

      int bufferOffset = offset - copyForRead.startingOffsetInFile;
      if (bufferOffset > copyForRead.bufferPosition) {
        throw new NoDataException("Requested address(=" + offset + ") points to un-existed data: " + appendBuffer);
      }

      UnsyncByteArrayInputStream is = new UnsyncByteArrayInputStream(
        copyForRead.getAppendBuffer(),
        bufferOffset,
        copyForRead.getBufferPosition()
      );
      return dataDescriptor.read(new DataInputStream(is));
    }

    if (offset >= fileLength) {
      throw new NoDataException("Requested address(=" + offset + ") points to un-existed data (file length: " + fileLength + ")");
    }
    // we do not need to flushAppendBuffer() since we store complete records
    DataStreamOverPagedStorage rs = TLOCAL_READ_STREAMS.get();

    rs.setup(storage, offset, fileLength);
    return dataDescriptor.read(rs);
  }

  @Override
  public boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException {
    //ensure no deadlocks on attempt escalate read->write lock:
    assert storageLock.getReadLockCount() == 0 : "Read-lock must not be held";

    int fileLengthLocal;
    lockWrite();
    try {
      flushAppendBuffer();
      fileLengthLocal = fileLength;
      if (fileLengthLocal == 0) {
        return true;
      }
    }
    finally {
      unlockWrite();
    }

    IOCancellationCallbackHolder.checkCancelled();

    //Since it is append-only storage => already-written records never modified => could be read without locking:
    //Newer records appended after unlockWrite() -- will not be read, which is expectable

    return storage.readInputStream(is -> {
      // calculation may restart few times, so it's expected that processor processes duplicates
      LimitedInputStream lis = new LimitedInputStream(new BufferedInputStream(is), fileLengthLocal) {
        @Override
        public int available() {
          return remainingLimit();
        }
      };
      DataInputStream valuesStream = new DataInputStream(lis);
      try {
        while (true) {
          int offset = lis.getBytesRead();
          Data value = dataDescriptor.read(valuesStream);
          if (!processor.process(offset, value)) return false;
        }
      }
      catch (EOFException e) {
        // Done
      }

      return true;
    });
  }

  @Override
  public int getCurrentLength() {
    return AppendMemoryBuffer.getBufferPosition(appendBuffer) + fileLength;
  }

  @Override
  public int append(Data value) throws IOException {
    BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
    DataOutput out = new DataOutputStream(bos);
    dataDescriptor.save(out, value);
    int size = bos.size();
    byte[] buffer = bos.getInternalBuffer();

    int currentLength = getCurrentLength();

    if (size > APPEND_BUFFER_SIZE) {
      flushAppendBuffer();
      storage.put(currentLength, buffer, 0, size);
      fileLength += size;
      if (appendBuffer != null) {
        appendBuffer = appendBuffer.rewind(fileLength);
      }
    }
    else {
      if (size > APPEND_BUFFER_SIZE - AppendMemoryBuffer.getBufferPosition(appendBuffer)) {
        flushAppendBuffer();
      }
      // myAppendBuffer will contain complete records
      if (appendBuffer == null) {
        appendBuffer = new AppendMemoryBuffer(fileLength);
      }
      appendBuffer.append(buffer, size);
    }
    return currentLength;
  }

  @Override
  public boolean checkBytesAreTheSame(int valueId,
                                      Data value) throws IOException {
    int offset = valueId;
    try (CheckerOutputStream comparer = buildOldComparerStream(offset)) {
      DataOutput out = new DataOutputStream(comparer);
      dataDescriptor.save(out, value);
      return comparer.same;
    }
  }

  private abstract static class CheckerOutputStream extends OutputStream {
    boolean same = true;
  }

  /**
   * @return fake OutputStream impl that doesn't write anything, but compare bytes to be written against
   * bytes already in a file on the same positions, and set .same to be true or false
   */
  private @NotNull CheckerOutputStream buildOldComparerStream(int startingOffsetInFile) throws IOException {
    if (fileLength <= startingOffsetInFile) {
      return new CheckerOutputStream() {
        private int address = startingOffsetInFile - fileLength;

        @Override
        public void write(int b) {
          if (same) {
            AppendMemoryBuffer buffer = appendBuffer;
            same = address < AppendMemoryBuffer.getBufferPosition(buffer)
                   && buffer.getAppendBuffer()[address++] == (byte)b;
          }
        }
      };
    }
    else {
      return new CheckerOutputStream() {
        private int offsetInFile = startingOffsetInFile;
        private int offsetInPage = storage.toOffsetInPage(startingOffsetInFile);
        private Page currentPage = storage.pageByOffset(startingOffsetInFile, /*forWrite: */false);
        private final int pageSize = storage.getPageSize();

        @Override
        public void write(int b) throws IOException {
          if (same) {
            if (pageSize == offsetInPage && offsetInFile < fileLength) {    // reached end of current byte buffer
              offsetInFile += offsetInPage;
              currentPage.close();
              currentPage = storage.pageByOffset(offsetInFile, /*forWrite: */ false);
              offsetInPage = 0;
            }
            same = offsetInPage < fileLength && currentPage.get(offsetInPage) == (byte)b;
            offsetInPage++;
          }
        }

        @Override
        public void close() {
          currentPage.close();
        }
      };
    }
  }

  private static final class DataStreamOverPagedStorage extends DataInputStream {
    private DataStreamOverPagedStorage() {
      super(new BufferedInputStreamOverPagedStorage());
    }

    void setup(PagedStorage storage, long pos, long limit) {
      ((BufferedInputStreamOverPagedStorage)in).setup(storage, pos, limit);
    }
  }

  private static final class BufferedInputStreamOverPagedStorage extends BufferedInputStream {
    BufferedInputStreamOverPagedStorage() {
      super(TOMBSTONE, 512);
    }

    void setup(@NotNull PagedStorage storage,
               long position,
               long limit) {
      this.pos = 0;
      this.count = 0;
      this.in = new InputStreamOverPagedStorage(storage, position, limit);
    }
  }

  private static final InputStream TOMBSTONE = new InputStream() {
    @Override
    public int read() {
      throw new IllegalStateException("should not happen");
    }
  };

  /**
   * The buffer caches in memory a region of a file [startingOffsetInFile..startingOffsetInFile+bufferPosition],
   * with both ends inclusive.
   */
  private static final class AppendMemoryBuffer {
    private final byte[] buffer;
    /**
     * Similar to ByteBuffer.position: a cursor pointing to the last written byte of a buffer.
     * I.e. (bufferPosition+1) is the next byte to be written.
     */
    private int bufferPosition;

    private final int startingOffsetInFile;

    private AppendMemoryBuffer(int startingOffsetInFile) {
      this(new byte[APPEND_BUFFER_SIZE], 0, startingOffsetInFile);
    }

    private AppendMemoryBuffer(byte[] buffer,
                               int bufferPosition,
                               int startingOffsetInFile) {
      this.buffer = buffer;
      this.startingOffsetInFile = startingOffsetInFile;
      this.bufferPosition = bufferPosition;
    }


    private byte[] getAppendBuffer() {
      return buffer;
    }

    private int getBufferPosition() {
      return bufferPosition;
    }

    public void append(byte[] buffer, int size) {
      System.arraycopy(buffer, 0, this.buffer, bufferPosition, size);
      bufferPosition += size;
    }

    public @NotNull AppendMemoryBuffer copy() {
      return new AppendMemoryBuffer(ByteArrays.copy(buffer), bufferPosition, startingOffsetInFile);
    }

    public @NotNull AppendMemoryBuffer rewind(int offsetInFile) {
      return new AppendMemoryBuffer(buffer, 0, offsetInFile);
    }

    @Override
    public String toString() {
      return "AppendMemoryBuffer[" + startingOffsetInFile + ".." + (startingOffsetInFile + bufferPosition) + "]";
    }

    private static int getBufferPosition(@Nullable AppendMemoryBuffer buffer) {
      return buffer != null ? buffer.bufferPosition : 0;
    }

    private static boolean hasChanges(@Nullable AppendMemoryBuffer buffer) {
      return buffer != null && buffer.getBufferPosition() > 0;
    }
  }
}