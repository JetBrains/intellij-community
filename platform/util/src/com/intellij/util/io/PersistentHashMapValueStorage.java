// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.MathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.AppendablePersistentMap.ValueDataAppender;
import com.intellij.util.io.PersistentMapImpl.CompactionRecordInfo;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Internal
public final class PersistentHashMapValueStorage {
  private @Nullable RAReader myCompactionModeReader;
  private volatile long mySize;
  private final Path myPath;
  private final @NotNull StorageLockContext myStorageLockContext;
  private final @NotNull ChannelAccessorBackedFileAccessor myFileAccessor;
  private final @NotNull SyncAbleBufferedOutputStreamOverFileAccessor myAppender;
  private final MyCompressedAppendableFile myCompressedAppendableFile;

  private final CreationTimeOptions myOptions;

  private boolean myCompactionMode;

  private static final long MAX_RETAINED_LIMIT_WHEN_COMPACTING = 100 * 1024 * 1024;

  public static final long SOFT_MAX_RETAINED_LIMIT = 10 * 1024 * 1024;
  public static final int BLOCK_SIZE_TO_WRITE_WHEN_SOFT_MAX_RETAINED_LIMIT_IS_HIT = 1024;

  /**
   * Default options for {@link PersistentHashMap} and {@link PersistentHashMapValueStorage}.
   * <br/>
   * Why: Both PHMap and its components (like PHMValueStorage) are frequently initialized quite deeply
   * inside some wrapping code. Also, it could be >1 instances of PHMap/ValueStorage to support single
   * top-level structure, and params like READONLY should be the same for all them. But it is quite
   * daunting to pass parameters like READONLY through all the ctors and intermediate methods down to
   * the point of actual initialization (e.g. READONLY is used in {@linkplain PagedFileStorage}
   * even). CreationTimeOptions allows to set those arguments in thread-local instance, and get them
   * there they are needed.
   * <br/>
   * Instances of the class are immutable, all mutation methods return new instances -- one could
   * safely keep any instance of this class for later use.
   */
  public static final class CreationTimeOptions {
    public static final ThreadLocal<Boolean> READONLY = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> HAS_NO_CHUNKS = new ThreadLocal<>();

    @VisibleForTesting
    @Internal
    public static final ThreadLocal<Boolean> DO_COMPRESSION = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return COMPRESSION_ENABLED;
      }
    };

    private final boolean myReadOnly;
    private final boolean myCompactChunksWithValueDeserialization;
    private final boolean myHasNoChunks;
    private final boolean myUseCompression;

    public CreationTimeOptions(boolean readOnly,
                               boolean compactChunksWithValueDeserialization,
                               boolean hasNoChunks,
                               boolean doCompression) {
      myReadOnly = readOnly;
      myCompactChunksWithValueDeserialization = compactChunksWithValueDeserialization;
      myHasNoChunks = hasNoChunks;
      myUseCompression = doCompression;
    }

    int getVersion() {
      return (myHasNoChunks ? 10 : 0) * 31 + (myUseCompression ? 0x13 : 0);
    }

    boolean isReadOnly() {
      return myReadOnly;
    }

    boolean useCompression() {
      return myUseCompression;
    }

    public CreationTimeOptions setReadOnly() {
      return new CreationTimeOptions(
        true,
        myCompactChunksWithValueDeserialization,
        myHasNoChunks,
        myUseCompression
      );
    }

    public CreationTimeOptions readOnly(final boolean readOnly) {
      return new CreationTimeOptions(
        readOnly,
        myCompactChunksWithValueDeserialization,
        myHasNoChunks,
        myUseCompression
      );
    }

    public CreationTimeOptions setCompactChunksWithValueDeserialization() {
      return new CreationTimeOptions(myReadOnly,
                                     true,
                                     myHasNoChunks, myUseCompression);
    }

    public CreationTimeOptions setHasNoChunks() {
      return new CreationTimeOptions(myReadOnly,
                                     myCompactChunksWithValueDeserialization,
                                     true,
                                     myUseCompression);
    }

    public <T, E extends Throwable> T with(final @NotNull ThrowableComputable<T, E> func) throws E {
      final CreationTimeOptions previousOptions = setThreadLocalOptions(this);
      try {
        return func.compute();
      }
      finally {
        setThreadLocalOptions(previousOptions);
      }
    }

    public static @NotNull CreationTimeOptions threadLocalOptions() {
      return new CreationTimeOptions(
        READONLY.get() == Boolean.TRUE,
        COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.get() == Boolean.TRUE,
        HAS_NO_CHUNKS.get() == Boolean.TRUE,
        DO_COMPRESSION.get() == Boolean.TRUE);
    }

    public static @NotNull CreationTimeOptions setThreadLocalOptions(final CreationTimeOptions options) {
      final CreationTimeOptions currentOptions = threadLocalOptions();
      READONLY.set(options.myReadOnly);
      COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(options.myCompactChunksWithValueDeserialization);
      HAS_NO_CHUNKS.set(options.myHasNoChunks);
      DO_COMPRESSION.set(options.myUseCompression);
      return currentOptions;
    }
  }

  @NotNull
  CreationTimeOptions getOptions() {
    return myOptions;
  }

  public static final boolean COMPRESSION_ENABLED = SystemProperties.getBooleanProperty("idea.compression.enabled", true);

  PersistentHashMapValueStorage(@NotNull Path path,
                                @NotNull CreationTimeOptions options,
                                @NotNull StorageLockContext storageLockContext) throws IOException {
    myPath = path;
    myOptions = options;
    myStorageLockContext = storageLockContext;
    myFileAccessor = new ChannelAccessorBackedFileAccessor(path, storageLockContext, options.myReadOnly);
    myAppender = new SyncAbleBufferedOutputStreamOverFileAccessor(myFileAccessor);

    if (myOptions.useCompression()) {
      myCompressedAppendableFile = new MyCompressedAppendableFile();
      mySize = myCompressedAppendableFile.length();
    }
    else {
      myCompressedAppendableFile = null;
      mySize = myFileAccessor.sizeIfExists();
    }
  }

  public long appendBytes(ByteArraySequence data, long prevChunkAddress) throws IOException {
    return appendBytes(data.getInternalBuffer(), data.getOffset(), data.getLength(), prevChunkAddress);
  }

  public long appendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    if (mySize == 0) {
      byte[] bytes = "Header Record For PersistentHashMapValueStorage".getBytes(StandardCharsets.UTF_8);
      doAppendBytes(bytes, 0, bytes.length, 0);

      // avoid corruption issue when disk fails to write first record synchronously or unexpected first write file increase (IDEA-106306),
      // code depends on correct value of mySize
      flushAppender();
      //Preserves the historical behavior: force the header record
      //TODO RC: think through, why do we need it/do we (still) need it ?
      myFileAccessor.force();

      long currentLength = myFileAccessor.sizeIfExists();
      if (currentLength > mySize) {  // if real file length (unexpectedly) increases
        Logger.getInstance(getClass()).info("Avoided PSHM corruption due to write failure:" + myPath);
        mySize = currentLength;  // volatile write
      }
    }

    return doAppendBytes(data, offset, dataLength, prevChunkAddress);
  }

  void checkAppendsAllowed(int previouslyAccumulatedChunkSize) {
    if (previouslyAccumulatedChunkSize != 0 && myOptions.myHasNoChunks) throw new AssertionError();
  }

  private long doAppendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    if (!allowedToCompactChunks()) throw new AssertionError();
    if (prevChunkAddress != 0 && myOptions.myHasNoChunks) throw new AssertionError();
    long currentChunkAddress = mySize; // volatile read

    DataOutputStream dataOutputStream;
    if (myCompressedAppendableFile != null) {
      dataOutputStream = new DataOutputStream(new OutputStream() {
        @Override
        public void write(byte @NotNull [] b, int off, int len) throws IOException {
          myCompressedAppendableFile.append(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
          byte[] r = {(byte)(b & 0xFF)};
          write(r);
        }
      });
    }
    else {
      dataOutputStream = toDataOutputStream(myAppender);
    }

    try {
      saveHeader(dataLength, prevChunkAddress, currentChunkAddress, dataOutputStream);
      dataOutputStream.write(data, offset, dataLength);
      mySize += dataOutputStream.resetWrittenBytesCount();  // volatile write
    }
    finally {
      dataOutputStream.close();
    }

    return currentChunkAddress;
  }

  private void saveHeader(int dataLength,
                          long prevChunkAddress,
                          long chunkAddress,
                          @NotNull DataOutputStream dataOutputStream) throws IOException {
    DataInputOutputUtil.writeINT(dataOutputStream, dataLength);
    if (!myOptions.myHasNoChunks) {
      if (chunkAddress < prevChunkAddress) {
        throw new IOException("writePrevChunkAddress:" + chunkAddress + "," + prevChunkAddress + "," + myPath);
      }
      long diff = chunkAddress - prevChunkAddress;
      DataInputOutputUtil.writeLONG(dataOutputStream, prevChunkAddress == 0 ? 0 : diff);
    }
  }

  private static final ThreadLocalCachedByteArray myBuffer = new ThreadLocalCachedByteArray();
  private static final int ourBufferLength = 1024;

  @Contract(mutates = "this,param1")
  private long compactValuesWithoutChunks(@NotNull List<CompactionRecordInfo> infos, @NotNull PersistentHashMapValueStorage storage)
    throws IOException {
    //infos = new ArrayList<PersistentHashMap.CompactionRecordInfo>(infos);
    infos.sort(Comparator.comparingLong(info -> info.valueAddress));

    final int fileBufferLength = 256 * 1024;
    final byte[] buffer = new byte[fileBufferLength];

    int fragments = 0;
    int newFragments = 0;

    byte[] outputBuffer = new byte[4096];

    long readStartOffset = -1;
    int bytesRead = -1;

    for (CompactionRecordInfo info : infos) {
      int recordStartInBuffer = (int)(info.valueAddress - readStartOffset);

      if (recordStartInBuffer + 5 > fileBufferLength || readStartOffset == -1) {
        readStartOffset = info.valueAddress;
        long remainingBytes = readStartOffset != -1 ? mySize - readStartOffset : mySize;
        bytesRead = remainingBytes < fileBufferLength ? (int)remainingBytes : fileBufferLength;

        myCompactionModeReader.get(readStartOffset, buffer, 0, bytesRead); // buffer contains [readStartOffset, readStartOffset + bytesRead)
        recordStartInBuffer = (int)(info.valueAddress - readStartOffset);
      }

      DataInputStream stream = toDataInputStream(buffer, recordStartInBuffer, buffer.length);
      int available = stream.available();
      int chunkSize = readChunkSize(stream);
      long prevChunkAddress = readPrevChunkAddress(info.valueAddress, stream);
      assert prevChunkAddress == 0;
      int dataOffset = available - stream.available() + recordStartInBuffer;

      if (chunkSize >= outputBuffer.length) {
        outputBuffer = new byte[(chunkSize / 4096 + 1) * 4096];
      }

      // dataOffset .. dataOffset + chunkSize
      int bytesFitInBuffer = Math.min(chunkSize, fileBufferLength - dataOffset);
      System.arraycopy(buffer, dataOffset, outputBuffer, 0, bytesFitInBuffer);

      while (bytesFitInBuffer != chunkSize) {
        readStartOffset += bytesRead;

        long remainingBytes = mySize - readStartOffset;
        bytesRead = remainingBytes < fileBufferLength ? (int)remainingBytes : fileBufferLength;

        myCompactionModeReader.get(readStartOffset, buffer, 0, bytesRead); // buffer contains [readStartOffset, readStartOffset + bytesRead)
        int newBytesFitInBuffer = Math.min(chunkSize - bytesFitInBuffer, fileBufferLength);
        System.arraycopy(buffer, 0, outputBuffer, bytesFitInBuffer, newBytesFitInBuffer);
        bytesFitInBuffer += newBytesFitInBuffer;
      }

      info.newValueAddress = storage.appendBytes(outputBuffer, 0, chunkSize, 0);

      ++fragments;
      ++newFragments;
    }

    return fragments | ((long)newFragments << 32);
  }

  @Contract(mutates = "this,param1")
  long compactValues(@NotNull List<CompactionRecordInfo> infos, @NotNull PersistentHashMapValueStorage storage) throws IOException {
    if (myOptions.myHasNoChunks) {
      return compactValuesWithoutChunks(infos, storage);
    }

    PriorityQueue<CompactionRecordInfo> records = new PriorityQueue<>(
      infos.size(), (info, info2) -> Long.compare(info2.valueAddress, info.valueAddress));

    records.addAll(infos);

    final int fileBufferLength = 256 * 1024;
    final int maxRecordHeader = 5 /* max length - variable int */ + 10 /* max long offset*/;
    final byte[] buffer = new byte[fileBufferLength + maxRecordHeader];
    byte[] reusedAccumulatedChunksBuffer = {};

    long lastReadOffset = mySize;
    long lastConsumedOffset = lastReadOffset;
    long allRecordsStart = 0;
    int fragments = 0;
    int newFragments = 0;

    byte[] stuffFromPreviousRecord = null;
    int bytesRead = (int)(mySize - (mySize / fileBufferLength) * fileBufferLength);
    long retained = 0;

    while (lastReadOffset != 0) {
      final long readStartOffset = lastReadOffset - bytesRead;
      myCompactionModeReader.get(readStartOffset, buffer, 0, bytesRead); // buffer contains [readStartOffset, readStartOffset + bytesRead)

      while (!records.isEmpty()) {
        final CompactionRecordInfo info = records.peek();
        if (info.valueAddress >= readStartOffset) {
          if (info.valueAddress >= lastReadOffset) {
            throw new IOException("Value storage is corrupted: value file size:" + mySize + ", readStartOffset:" + readStartOffset +
                                  ", record address:" + info.valueAddress + "; file: " + myPath);
          }
          // record start is inside our buffer

          final int recordStartInBuffer = (int)(info.valueAddress - readStartOffset);
          DataInputStream inputStream = toDataInputStream(buffer, recordStartInBuffer, buffer.length);

          if (stuffFromPreviousRecord != null && fileBufferLength - recordStartInBuffer < maxRecordHeader) {
            // add additional bytes to read offset / size
            if (allRecordsStart != 0) {
              myCompactionModeReader.get(allRecordsStart, buffer, bytesRead, maxRecordHeader);
            }
            else {
              final int maxAdditionalBytes = Math.min(stuffFromPreviousRecord.length, maxRecordHeader);
              System.arraycopy(stuffFromPreviousRecord, 0, buffer, bytesRead, maxAdditionalBytes);
            }
          }

          int available = inputStream.available();
          int chunkSize = readChunkSize(inputStream);
          final long prevChunkAddress = readPrevChunkAddress(info.valueAddress, inputStream);
          final int dataOffset = available - inputStream.available();

          byte[] accumulatedChunksBuffer;
          if (info.value != null) {
            int defragmentedChunkSize = info.value.length + chunkSize;
            if (prevChunkAddress == 0) {
              if (defragmentedChunkSize >= reusedAccumulatedChunksBuffer.length) {
                reusedAccumulatedChunksBuffer = new byte[defragmentedChunkSize];
              }
              accumulatedChunksBuffer = reusedAccumulatedChunksBuffer;
            }
            else {
              accumulatedChunksBuffer = new byte[defragmentedChunkSize];
              retained += defragmentedChunkSize;
            }
            System.arraycopy(info.value, 0, accumulatedChunksBuffer, chunkSize, info.value.length);
          }
          else {
            if (prevChunkAddress == 0) {
              if (chunkSize >= reusedAccumulatedChunksBuffer.length) reusedAccumulatedChunksBuffer = new byte[chunkSize];
              accumulatedChunksBuffer = reusedAccumulatedChunksBuffer;
            }
            else {
              accumulatedChunksBuffer = new byte[chunkSize];
              retained += chunkSize;
            }
          }

          final int chunkSizeOutOfBuffer = MathUtil.clamp((int)(info.valueAddress + dataOffset + chunkSize - lastReadOffset), 0, chunkSize);
          if (chunkSizeOutOfBuffer > 0) {
            if (allRecordsStart != 0) {
              myCompactionModeReader.get(allRecordsStart, accumulatedChunksBuffer, chunkSize - chunkSizeOutOfBuffer, chunkSizeOutOfBuffer);
            }
            else {
              int offsetInStuffFromPreviousRecord = Math.max((int)(info.valueAddress + dataOffset - lastReadOffset), 0);
              // stuffFromPreviousRecord starts from lastReadOffset
              System.arraycopy(stuffFromPreviousRecord, offsetInStuffFromPreviousRecord, accumulatedChunksBuffer,
                               chunkSize - chunkSizeOutOfBuffer, chunkSizeOutOfBuffer);
            }
          }

          stuffFromPreviousRecord = null;
          allRecordsStart = 0;

          lastConsumedOffset = info.valueAddress;
          checkPreconditions(accumulatedChunksBuffer, chunkSize);

          System.arraycopy(buffer, recordStartInBuffer + dataOffset, accumulatedChunksBuffer, 0, chunkSize - chunkSizeOutOfBuffer);

          ++fragments;
          records.remove(info);
          if (info.value != null) {
            chunkSize += info.value.length;
            retained -= info.value.length;
            info.value = null;
          }

          if (prevChunkAddress == 0) {
            info.newValueAddress = storage.appendBytes(accumulatedChunksBuffer, 0, chunkSize, info.newValueAddress);
            ++newFragments;
          }
          else {
            if (retained > SOFT_MAX_RETAINED_LIMIT &&
                accumulatedChunksBuffer.length > BLOCK_SIZE_TO_WRITE_WHEN_SOFT_MAX_RETAINED_LIMIT_IS_HIT ||
                retained > MAX_RETAINED_LIMIT_WHEN_COMPACTING) {
              // to avoid OOME we need to save bytes in accumulatedChunksBuffer
              newFragments +=
                saveAccumulatedDataOnDiskPreservingWriteOrder(storage, info, prevChunkAddress, accumulatedChunksBuffer, chunkSize);
              retained -= accumulatedChunksBuffer.length;
              continue;
            }
            info.value = accumulatedChunksBuffer;
            info.valueAddress = prevChunkAddress;
            records.add(info);
          }
        }
        else {
          // [readStartOffset,lastConsumedOffset) is from previous segment
          if (stuffFromPreviousRecord == null) {
            stuffFromPreviousRecord = new byte[(int)(lastConsumedOffset - readStartOffset)];
            System.arraycopy(buffer, 0, stuffFromPreviousRecord, 0, stuffFromPreviousRecord.length);
          }
          else {
            allRecordsStart = readStartOffset;
          }
          break; // request next read
        }
      }

      lastReadOffset -= bytesRead;
      bytesRead = fileBufferLength;
    }

    return fragments | ((long)newFragments << 32);
  }

  private int saveAccumulatedDataOnDiskPreservingWriteOrder(PersistentHashMapValueStorage storage,
                                                            CompactionRecordInfo info,
                                                            long prevChunkAddress,
                                                            byte[] accumulatedChunksData,
                                                            int accumulatedChunkDataLength) throws IOException {
    ReadResult result = readBytes(prevChunkAddress);
    // to avoid possible OOME result.bytes and accumulatedChunksData are not combined in one chunk, instead they are
    // placed one after another, such near placement should be fine because of disk caching
    info.newValueAddress = storage.appendBytes(result.buffer, 0, result.buffer.length, info.newValueAddress);
    info.newValueAddress = storage.appendBytes(accumulatedChunksData, 0, accumulatedChunkDataLength, info.newValueAddress);

    info.value = null;
    info.valueAddress = 0;
    return 2; // number of chunks produced = number of appendBytes called
  }

  static final class ReadResult {
    final byte[] buffer;
    final int chunksCount;

    ReadResult(byte[] buffer, int chunksCount) {
      this.buffer = buffer;
      this.chunksCount = chunksCount;
    }
  }

  private long myChunksRemovalTime;
  private long myChunksReadingTime;
  private int myChunks;
  private long myChunksOriginalBytes;
  private long myChunksBytesAfterRemoval;
  private int myLastReportedChunksCount;

  /**
   * Reads bytes pointed by tailChunkAddress, returns new address if linked list compactification have been performed
   */
  ReadResult readBytes(long tailChunkAddress) throws IOException {
    flushAppender();

    checkCancellation();
    long startedTime = ourDumpChunkRemovalTime ? System.nanoTime() : 0;

    RAReader reader = myCompactionModeReader;
    if (myCompressedAppendableFile != null) {
      reader = new ReaderOverCompressedFile(myCompressedAppendableFile);
    }
    if (reader == null) {
      reader = new ReaderOverFileAccessor(myFileAccessor);
    }

    int chunkCount = 0;
    byte[] result = null;
    try {
      long chunk = tailChunkAddress;
      while (chunk != 0) {
        if (chunk < 0 || chunk > mySize) throw new CorruptedException(myPath);

        byte[] buffer = myBuffer.getBuffer(ourBufferLength);
        int len = (int)Math.min(ourBufferLength, mySize - chunk);

        reader.get(chunk, buffer, 0, len);
        DataInputStream inputStream = toDataInputStream(buffer, 0, len);

        final int chunkSize = readChunkSize(inputStream);
        final long prevChunkAddress = readPrevChunkAddress(chunk, inputStream);
        final int headerOffset = len - inputStream.available();

        byte[] b = new byte[(result != null ? result.length : 0) + chunkSize];
        if (result != null) System.arraycopy(result, 0, b, b.length - result.length, result.length);
        result = b;

        checkPreconditions(result, chunkSize);
        if (chunkSize < ourBufferLength - headerOffset) {
          System.arraycopy(buffer, headerOffset, result, 0, chunkSize);
        }
        else {
          reader.get(chunk + headerOffset, result, 0, chunkSize);
        }

        if (prevChunkAddress >= chunk) throw new CorruptedException(myPath);

        chunk = prevChunkAddress;
        chunkCount++;

        if (prevChunkAddress != 0) {
          checkCancellation();
          assert !myOptions.myHasNoChunks;
        }
        if (result.length > mySize && myCompressedAppendableFile == null) {
          throw new CorruptedException(myPath);
        }
      }
    }
    catch (OutOfMemoryError error) {
      throw new CorruptedException(myPath);
    }

    if (chunkCount > 1) {
      checkCancellation();

      myChunksReadingTime += (ourDumpChunkRemovalTime ? System.nanoTime() : 0) - startedTime;
      myChunks += chunkCount;
      myChunksOriginalBytes += result.length;
    }

    return new ReadResult(result, chunkCount);
  }

  private boolean allowedToCompactChunks() {
    return !myCompactionMode && !myOptions.myReadOnly;
  }

  boolean performChunksCompaction(int chunksCount) {
    return chunksCount > 1 && allowedToCompactChunks();
  }

  long compactChunks(@NotNull ValueDataAppender appender, @NotNull ReadResult result) throws IOException {
    checkCancellation();
    long startedTime = ourDumpChunkRemovalTime ? System.nanoTime() : 0;
    long newValueOffset;

    if (myOptions.myCompactChunksWithValueDeserialization) {
      final BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream(result.buffer.length);
      DataOutputStream testStream = new DataOutputStream(stream);
      appender.append(testStream);
      newValueOffset = appendBytes(stream.toByteArraySequence(), 0);
      myChunksBytesAfterRemoval += stream.size();
    }
    else {
      newValueOffset = appendBytes(ByteArraySequence.create(result.buffer), 0);
      myChunksBytesAfterRemoval += result.buffer.length;
    }

    if (ourDumpChunkRemovalTime) {
      myChunksRemovalTime += System.nanoTime() - startedTime;

      if (myChunks - myLastReportedChunksCount > 1000) {
        myLastReportedChunksCount = myChunks;
        System.out.println(myChunks + " chunks were read " + (myChunksReadingTime / 1000000) +
                           "ms, bytes: " + myChunksOriginalBytes +
                           (myChunksOriginalBytes != myChunksBytesAfterRemoval ? "->" + myChunksBytesAfterRemoval : "") +
                           " compaction:" + (myChunksRemovalTime / 1000000) + "ms in " + myPath);
      }
    }

    return newValueOffset;
  }

  private static final boolean ourDumpChunkRemovalTime = SystemProperties.getBooleanProperty("idea.phmp.dump.chunk.removal.time", false);

  // hook for exceptional termination of long io operation
  private static void checkCancellation() {
    IOCancellationCallbackHolder.checkCancelled();
  }

  private static int readChunkSize(@NotNull DataInputStream in) throws IOException {
    final int chunkSize = DataInputOutputUtil.readINT(in);
    if (chunkSize < 0) {
      throw new IOException("Value storage corrupted: negative chunk size: " + chunkSize);
    }
    return chunkSize;
  }

  private long readPrevChunkAddress(long chunkAddress, @NotNull DataInputStream in) throws IOException {
    if (myOptions.myHasNoChunks) return 0;
    final long prevOffsetDiff = DataInputOutputUtil.readLONG(in);
    if (prevOffsetDiff >= chunkAddress) {
      throw new IOException("readPrevChunkAddress:" + chunkAddress + "," + prevOffsetDiff + "," + mySize + "," + myPath);
    }
    return prevOffsetDiff != 0 ? chunkAddress - prevOffsetDiff : 0;
  }

  public long getSize() {
    return mySize;
  }

  private static void checkPreconditions(final byte[] result, final int chunkSize) throws IOException {
    if (chunkSize < 0) {
      throw new IOException("Value storage corrupted: negative chunk size");
    }
    if (chunkSize > result.length) {
      throw new IOException("Value storage corrupted");
    }
  }

  public void flush() {
    if (myOptions.myReadOnly) return;
    if (myCompressedAppendableFile != null) {
      myCompressedAppendableFile.force();
    }
    if (mySize < 0) assert false;  // volatile read
    flushAppender();
  }

  private void flushAppender() {
    flushAppender(myAppender);
  }

  private static void flushAppender(@NotNull SyncAbleBufferedOutputStreamOverFileAccessor appender) {
    try {
      appender.flushBufferedBytes();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void disposeAppender(@NotNull SyncAbleBufferedOutputStreamOverFileAccessor appender) {
    try {
      appender.dispose();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void closeFileAccessor(@NotNull ChannelAccessorBackedFileAccessor fileAccessor) {
    try {
      fileAccessor.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Checks all channel-backed files owned by this value storage against both mode-bound cache views. */
  void assertNoOpenChannels() {
    myFileAccessor.assertNoOpenChannels();
    if (myCompressedAppendableFile != null) {
      myCompressedAppendableFile.assertNoOpenChannels();
    }
  }

  public void dispose() {
    try {
      if (myCompressedAppendableFile != null) myCompressedAppendableFile.dispose();
    }
    finally {
      if (mySize < 0) assert false; // volatile read
      disposeAppender(myAppender);
      closeFileAccessor(myFileAccessor);

      if (myCompactionModeReader != null) {
        try {
          myCompactionModeReader.dispose();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        myCompactionModeReader = null;
      }
      assertNoOpenChannels();
    }
  }

  void switchToCompactionMode() {
    if (myCompressedAppendableFile != null) {
      myCompactionModeReader = new ReaderOverCompressedFile(myCompressedAppendableFile);
    }
    else {
      myCompactionModeReader = new ReaderOverFileAccessor(myFileAccessor);
    }

    myCompactionMode = true;
  }

  private static @NotNull DataInputStream toDataInputStream(byte @NotNull [] buffer, int offset, int length) {
    return new DataInputStream(new UnsyncByteArrayInputStream(buffer, offset, length));
  }

  private static @NotNull DataOutputStream toDataOutputStream(@NotNull SyncAbleBufferedOutputStreamOverFileAccessor appender) {
    return new DataOutputStream(appender) {
      @Override
      public void close() throws IOException {
        flush();
      }
    };
  }

  public static PersistentHashMapValueStorage create(@NotNull Path path,
                                                     @NotNull CreationTimeOptions options,
                                                     @NotNull StorageLockContext storageLockContext) throws IOException {
    return new PersistentHashMapValueStorage(path, options, storageLockContext);
  }

  public static PersistentHashMapValueStorage create(@NotNull Path path, @NotNull CreationTimeOptions options) throws IOException {
    return create(path, options, PagedFileStorage.lookupStorageContext(null));
  }

  private interface RAReader {
    void get(long addr, byte[] dst, int off, int len) throws IOException;

    void dispose() throws IOException;
  }

  private static final class ChannelAccessorBackedFileAccessor {
    private static final boolean CHECK_CONSISTENCY =
      SystemProperties.getBooleanProperty("idea.do.random.access.wrapper.assertions", false);

    private final @NotNull Path myPath;
    private final @NotNull StorageLockContext myStorageLockContext;
    private final @NotNull ChannelsAccessor myChannelsAccessor;
    private final boolean myReadOnly;

    private long myAppendAtOffset;

    ChannelAccessorBackedFileAccessor(@NotNull Path path,
                                      @NotNull StorageLockContext storageLockContext,
                                      boolean readOnly) throws IOException {
      myPath = path;
      myStorageLockContext = storageLockContext;
      myChannelsAccessor = storageLockContext.getChannelsAccessor(readOnly);
      myReadOnly = readOnly;
      myAppendAtOffset = initialSize();
    }

    synchronized void append(byte @NotNull [] src, int off, int len) throws IOException {
      if (myReadOnly) {
        throw new IOException("Value storage is read-only: " + myPath);
      }

      if (CHECK_CONSISTENCY) {
        assert myAppendAtOffset == size();
      }

      long offset = myAppendAtOffset;
      myAppendAtOffset += len;
      ByteBuffer buffer = ByteBuffer.wrap(src, off, len);
      myChannelsAccessor.executeOp(myPath, channel -> {
        long writeOffset = offset;
        while (buffer.hasRemaining()) {
          int written = channel.write(buffer, writeOffset);
          if (written <= 0) {
            throw new IOException("Failed to append data to " + myPath + ": channel made no progress");
          }
          writeOffset += written;
        }
        return null;
      });
    }

    int read(long addr, byte @NotNull [] dst, int off, int len) throws IOException {
      return myChannelsAccessor.executeOp(myPath, channel -> channel.read(ByteBuffer.wrap(dst, off, len), addr));
    }

    /** Replaces side-file content while keeping FileChannel lifetime scoped to the accessor operation. */
    synchronized void replace(byte @NotNull [] src, int len) throws IOException {
      if (myReadOnly) {
        throw new IOException("Value storage is read-only: " + myPath);
      }

      ByteBuffer buffer = ByteBuffer.wrap(src, 0, len);
      myChannelsAccessor.executeOp(myPath, channel -> {
        long previousSize = channel.size();
        long writeOffset = 0;
        while (buffer.hasRemaining()) {
          int written = channel.write(buffer, writeOffset);
          if (written <= 0) {
            throw new IOException("Failed to replace data in " + myPath + ": channel made no progress");
          }
          writeOffset += written;
        }
        if (len < previousSize) {
          channel.truncate(len);
        }
        return null;
      });
      myAppendAtOffset = len;
    }

    /** Clears side-file content if it exists and is non-empty. */
    synchronized void clearIfNonEmpty() throws IOException {
      if (myReadOnly || !Files.exists(myPath)) return;

      myChannelsAccessor.executeOp(myPath, channel -> {
        if (channel.size() == 0) return null;
        channel.truncate(0);
        return null;
      });
      myAppendAtOffset = 0;
    }

    /** Forces pending writes to the backing file. This is a durability/WAL-drain operation, not a logical visibility barrier. */
    void force() throws IOException {
      if (myReadOnly) return;
      myChannelsAccessor.executeOp(myPath, channel -> {
        channel.force(true);
        return null;
      });
    }

    void close() throws IOException {
      try {
        myChannelsAccessor.closeChannel(myPath);
      }
      finally {
        assertNoOpenChannels();
      }
    }

    /** Checks that no cached channel remains for this accessor's file in either mode-bound view. */
    void assertNoOpenChannels() {
      myStorageLockContext.assertNoOpenChannels(myPath);
    }

    private long sizeIfExists() throws IOException {
      return initialSize();
    }

    private long initialSize() throws IOException {
      return Files.exists(myPath) ? size() : 0;
    }

    private long size() throws IOException {
      return myChannelsAccessor.executeOp(myPath, channel -> channel.size());
    }

    @Override
    public synchronized String toString() {
      return myPath + " [appendAt: " + myAppendAtOffset + "] @" + Integer.toHexString(hashCode());
    }
  }

  private static final class ReaderOverCompressedFile implements RAReader {
    private final @NotNull CompressedAppendableFile myCompressedAppendableFile;

    ReaderOverCompressedFile(@NotNull CompressedAppendableFile compressedAppendableFile) {
      myCompressedAppendableFile = compressedAppendableFile;
    }

    @Override
    public void get(long addr, byte[] dst, int off, int len) throws IOException {
      try (DataInputStream stream = myCompressedAppendableFile.getStream(addr)) {
        stream.readFully(dst, off, len);
      }
    }

    @Override
    public void dispose() {
    }
  }

  private static final class ReaderOverFileAccessor implements RAReader {
    private final @NotNull ChannelAccessorBackedFileAccessor myFileAccessor;

    private ReaderOverFileAccessor(@NotNull ChannelAccessorBackedFileAccessor fileAccessor) {
      myFileAccessor = fileAccessor;
    }

    @Override
    public void get(final long addr, final byte[] dst, final int off, final int len) throws IOException {
      myFileAccessor.read(addr, dst, off, len);
    }

    @Override
    public void dispose() {
    }
  }

  /** Presents accessor-backed side-file reads as a stream without retaining a FileChannel between reads. */
  private static final class InputStreamOverFileAccessor extends InputStream {
    private final @NotNull ChannelAccessorBackedFileAccessor myFileAccessor;
    private final long myLimit;
    private long myPosition;

    InputStreamOverFileAccessor(@NotNull ChannelAccessorBackedFileAccessor fileAccessor, long limit) {
      myFileAccessor = fileAccessor;
      myLimit = limit;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
      if (len == 0) return 0;
      if (myPosition >= myLimit) return -1;

      int bytesToRead = (int)Math.min(len, myLimit - myPosition);
      int bytesRead = myFileAccessor.read(myPosition, b, off, bytesToRead);
      if (bytesRead < 0) return -1;

      myPosition += bytesRead;
      return bytesRead;
    }

    @Override
    public int read() throws IOException {
      byte[] buffer = new byte[1];
      int read = read(buffer);
      return read == -1 ? -1 : buffer[0] & 0xFF;
    }

    @Override
    public int available() {
      return (int)Math.min(Integer.MAX_VALUE, Math.max(0, myLimit - myPosition));
    }
  }

  private static final class SyncAbleBufferedOutputStreamOverFileAccessor extends BufferedOutputStream {
    SyncAbleBufferedOutputStreamOverFileAccessor(@NotNull ChannelAccessorBackedFileAccessor fileAccessor) {
      super(new OutputStreamOverFileAccessor(fileAccessor));
    }

    /** Flushes Java-level buffering so later channel reads in this process can see appended bytes. */
    void flushBufferedBytes() throws IOException {
      flush();
    }

    public void dispose() throws IOException {
      flush();
    }

    @Override
    public void close() throws IOException {
      flush();
    }

    private static final class OutputStreamOverFileAccessor extends OutputStream {
      private final @NotNull ChannelAccessorBackedFileAccessor myFileAccessor;

      private OutputStreamOverFileAccessor(@NotNull ChannelAccessorBackedFileAccessor fileAccessor) {
        myFileAccessor = fileAccessor;
      }

      @Override
      public void write(byte @NotNull [] b, int off, int len) throws IOException {
        myFileAccessor.append(b, off, len);
      }

      @Override
      public void write(int b) throws IOException {
        byte[] r = {(byte)(b & 0xFF)};
        write(r);
      }
    }
  }

  private final class MyCompressedAppendableFile extends CompressedAppendableFile {
    private final @NotNull ChannelAccessorBackedFileAccessor myChunkLengthFileAccessor;
    private final @NotNull SyncAbleBufferedOutputStreamOverFileAccessor myChunkLengthAppender;
    private final @NotNull ChannelAccessorBackedFileAccessor myIncompleteChunkFileAccessor;

    MyCompressedAppendableFile() throws IOException {
      super(myPath);
      myChunkLengthFileAccessor = new ChannelAccessorBackedFileAccessor(getChunkLengthFile(), myStorageLockContext, myOptions.myReadOnly);
      myChunkLengthAppender = new SyncAbleBufferedOutputStreamOverFileAccessor(myChunkLengthFileAccessor);
      myIncompleteChunkFileAccessor =
        new ChannelAccessorBackedFileAccessor(getIncompleteChunkFile(), myStorageLockContext, myOptions.myReadOnly);
    }

    @Override
    protected @NotNull InputStream getChunkInputStream(long offset, int pageSize) throws IOException {
      flushAppender();
      byte[] bytes = new byte[pageSize];
      myFileAccessor.read(offset, bytes, 0, pageSize);
      return new ByteArrayInputStream(bytes);
    }

    @Override
    protected @NotNull DataOutputStream getChunkAppendStream() {
      return toDataOutputStream(myAppender);
    }

    @Override
    protected @NotNull DataOutputStream getChunkLengthAppendStream() {
      return toDataOutputStream(myChunkLengthAppender);
    }

    @Override
    protected long getChunkLengthFileSize() throws IOException {
      return myChunkLengthFileAccessor.sizeIfExists();
    }

    @Override
    protected @NotNull InputStream getChunkLengthInputStream(long limit) {
      return new BufferedInputStream(new InputStreamOverFileAccessor(myChunkLengthFileAccessor, limit), 32768);
    }

    @Override
    protected long getIncompleteChunkFileSize() throws IOException {
      return myIncompleteChunkFileAccessor.sizeIfExists();
    }

    @Override
    protected @NotNull InputStream getIncompleteChunkInputStream(long limit) {
      return new InputStreamOverFileAccessor(myIncompleteChunkFileAccessor, limit);
    }

    @Override
    protected void writeIncompleteChunkFile(byte @NotNull [] buffer, int length) throws IOException {
      myIncompleteChunkFileAccessor.replace(buffer, length);
    }

    @Override
    protected void deleteIncompleteChunkFileIfExists() throws IOException {
      myIncompleteChunkFileAccessor.clearIfNonEmpty();
    }

    @Override
    protected @NotNull Path getChunksFile() {
      return myPath;
    }

    @Override
    public synchronized void force() {
      super.force();
      flushAppender(myChunkLengthAppender);
    }

    @Override
    public synchronized void dispose() {
      super.dispose();

      disposeAppender(myChunkLengthAppender);
      closeFileAccessor(myChunkLengthFileAccessor);
      closeFileAccessor(myIncompleteChunkFileAccessor);
      assertNoOpenChannels();
    }

    /** Checks compressed side-files against both mode-bound cache views. */
    private void assertNoOpenChannels() {
      myChunkLengthFileAccessor.assertNoOpenChannels();
      myIncompleteChunkFileAccessor.assertNoOpenChannels();
    }
  }

  @TestOnly
  public boolean isReadOnly() {
    return myOptions.myReadOnly;
  }

  @TestOnly
  public @NotNull StorageLockContext getStorageLockContext() {
    return myStorageLockContext;
  }
}
