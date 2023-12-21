// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.nio.file.Path;

/** valueId == offset of value in a file */
public final class AppendableStorageBackedByResizableMappedFile<Data> implements AppendableObjectStorage<Data> {
  @VisibleForTesting
  @ApiStatus.Internal
  public static final int APPEND_BUFFER_SIZE = 4096;

  private static final ThreadLocal<MyDataIS> TLOCAL_READ_STREAMS = ThreadLocal.withInitial(() -> new MyDataIS());

  private final ResizeableMappedFile storage;

  //TODO RC: the class is suspicious because it uses some multi-threading constructs (synchronized, volatile), but
  //         the class overall is not thread safe in any reasonable sense. I.e. authors seem to consider the class
  //         for multithreaded use, but it is really not safe for it. We should either made it really thread-safe,
  //         or remove all synchronized/volatile, and rely on caller for synchronization.

  private volatile int fileLength;
  private volatile @Nullable AppendMemoryBuffer appendBuffer;

  private final @NotNull DataExternalizer<Data> dataDescriptor;

  public AppendableStorageBackedByResizableMappedFile(@NotNull Path file,
                                                      int initialSize,
                                                      @Nullable StorageLockContext lockContext,
                                                      int pageSize,
                                                      boolean valuesAreBufferAligned,
                                                      @NotNull DataExternalizer<Data> dataDescriptor) throws IOException {
    this.storage = new ResizeableMappedFile(
      file,
      initialSize,
      lockContext,
      pageSize,
      valuesAreBufferAligned
    );
    this.dataDescriptor = dataDescriptor;
    fileLength = Math.toIntExact(storage.length());
  }

  //requires storage.lockWrite()
  private void flushAppendBuffer() throws IOException {
    if (AppendMemoryBuffer.hasChanges(appendBuffer)) {
      int bufferPosition = appendBuffer.getBufferPosition();
      storage.put(fileLength, appendBuffer.getAppendBuffer(), 0, bufferPosition);
      fileLength += bufferPosition;
      appendBuffer = appendBuffer.rewind(fileLength);
    }
  }


  @Override
  public Data read(int valueId, boolean checkAccess) throws IOException {
    int offset = valueId;
    AppendMemoryBuffer buffer = appendBuffer;
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
    MyDataIS rs = TLOCAL_READ_STREAMS.get();

    rs.setup(storage, offset, fileLength, checkAccess);
    return dataDescriptor.read(rs);
  }

  @Override
  public boolean processAll(@NotNull StorageObjectProcessor<? super Data> processor) throws IOException {
    //ensure no deadlocks on attempt to escalate read->write lock:
    storage.getStorageLockContext().checkReadLockNotHeld();

    int fileLengthLocal;
    lockWrite();
    try {
      force();

      fileLengthLocal = fileLength;
      if (fileLengthLocal == 0) {
        return true;
      }
    }
    finally {
      unlockWrite();
    }

    //Since it is append-only storage => already-written records never modified => could be read without locking:
    //Newer records appended after unlockWrite() -- will not be read, which is expectable

    IOCancellationCallbackHolder.checkCancelled();
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
    final int size = bos.size();
    final byte[] buffer = bos.getInternalBuffer();

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
  public boolean checkBytesAreTheSame(int valueId, Data value) throws IOException {
    int offset = valueId;
    try (CheckerOutputStream comparer = buildOldComparerStream(offset)) {
      DataOutput out = new DataOutputStream(comparer);
      dataDescriptor.save(out, value);
      return comparer.same;
    }
  }

  @Override
  public void clear() throws IOException {
    storage.clear();
    fileLength = 0;
    appendBuffer = null;
  }

  @Override
  public boolean isDirty() {
    return AppendMemoryBuffer.hasChanges(appendBuffer) || storage.isDirty();
  }

  //requires storage.lockWrite()
  @Override
  public void force() throws IOException {
    flushAppendBuffer();
    storage.force();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't .close() appendable storage [" + storage.getPagedFileStorage().getFile() + "]"),
      this::force,
      storage::close
    );
  }

  @Override
  public void lockRead() {
    storage.lockRead();
  }

  @Override
  public void unlockRead() {
    storage.unlockRead();
  }

  @Override
  public void lockWrite() {
    storage.lockWrite();
  }

  @Override
  public void unlockWrite() {
    storage.unlockWrite();
  }

  private abstract static class CheckerOutputStream extends OutputStream {
    boolean same = true;
  }

  /**
   * @return fake OutputStream impl that doesn't write anything, but compare bytes to be written against
   * bytes already in a file on the same positions, and set .same to be true or false
   */
  private @NotNull CheckerOutputStream buildOldComparerStream(final int startingOffsetInFile) throws IOException {
    final PagedFileStorage storage = this.storage.getPagedFileStorage();

    if (fileLength <= startingOffsetInFile) {
      return new CheckerOutputStream() {
        private int address = startingOffsetInFile - fileLength;

        @Override
        public void write(int b) {
          if (same) {
            same = address < AppendMemoryBuffer.getBufferPosition(appendBuffer) && appendBuffer.getAppendBuffer()[address++] == (byte)b;
          }
        }
      };
    }
    else {
      return new CheckerOutputStream() {
        private int offsetInFile = startingOffsetInFile;
        private int offsetInPage = storage.getOffsetInPage(startingOffsetInFile);
        private DirectBufferWrapper buffer = storage.getByteBuffer(startingOffsetInFile, false);
        private final int pageSize = storage.getPageSize();

        @Override
        public void write(int b) throws IOException {
          if (same) {
            if (pageSize == offsetInPage && offsetInFile < fileLength) {    // reached end of current byte buffer
              offsetInFile += offsetInPage;
              buffer.unlock();
              buffer = storage.getByteBuffer(offsetInFile, false);
              offsetInPage = 0;
            }
            same = offsetInFile < fileLength && buffer.get(offsetInPage++, true) == (byte)b;
          }
        }

        @Override
        public void close() {
          buffer.unlock();
        }
      };
    }
  }

  private static final class MyDataIS extends DataInputStream {
    private MyDataIS() {
      super(new MyBufferedIS());
    }

    void setup(ResizeableMappedFile is, long pos, long limit, boolean checkAccess) {
      ((MyBufferedIS)in).setup(is, pos, limit, checkAccess);
    }
  }

  private static final class MyBufferedIS extends BufferedInputStream {
    MyBufferedIS() {
      super(TOMBSTONE, 512);
    }

    void setup(ResizeableMappedFile in, long pos, long limit, boolean checkAccess) {
      this.pos = 0;
      this.count = 0;
      this.in = new MappedFileInputStream(in, pos, limit, checkAccess);
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


    private synchronized byte[] getAppendBuffer() {
      return buffer;
    }

    private synchronized int getBufferPosition() {
      return bufferPosition;
    }

    public synchronized void append(byte[] buffer, int size) {
      System.arraycopy(buffer, 0, this.buffer, bufferPosition, size);
      bufferPosition += size;
    }

    public synchronized @NotNull AppendMemoryBuffer copy() {
      return new AppendMemoryBuffer(ByteArrays.copy(buffer), bufferPosition, startingOffsetInFile);
    }

    public synchronized @NotNull AppendMemoryBuffer rewind(int offsetInFile) {
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