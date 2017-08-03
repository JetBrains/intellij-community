/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PersistentHashMapValueStorage {
  @Nullable
  private RAReader myCompactionModeReader = null;
  private volatile long mySize;
  private final File myFile;
  private final String myPath;
  private final boolean myReadOnly;
  private final boolean myCompactChunksWithValueDeserialization;
  private final ExceptionalIOCancellationCallback myExceptionalIOCancellationCallback;
  private boolean myCompactionMode = false;

  private static final int CACHE_PROTECTED_QUEUE_SIZE = 10;
  private static final int CACHE_PROBATIONAL_QUEUE_SIZE = 20;

  public static class CreationTimeOptions {
    public static final ThreadLocal<ExceptionalIOCancellationCallback> EXCEPTIONAL_IO_CANCELLATION = new ThreadLocal<ExceptionalIOCancellationCallback>();
    public static final ThreadLocal<Boolean> READONLY = new ThreadLocal<Boolean>();
    public static final ThreadLocal<Boolean> COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION = new ThreadLocal<Boolean>();
    
    public static final ThreadLocal<Boolean> DO_COMPRESSION = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return COMPRESSION_ENABLED;
      }
    };
  }

  public interface ExceptionalIOCancellationCallback {
    void checkCancellation();
  }

  // cache size is twice larger than constants because (when used) it replaces two caches
  private static final FileAccessorCache<String, RandomAccessFileWithLengthAndSizeTracking> ourRandomAccessFileCache = new FileAccessorCache<String, RandomAccessFileWithLengthAndSizeTracking>(
    2*CACHE_PROTECTED_QUEUE_SIZE, 2*CACHE_PROBATIONAL_QUEUE_SIZE) {
    @Override
    protected RandomAccessFileWithLengthAndSizeTracking createAccessor(String path) throws IOException {
      return new RandomAccessFileWithLengthAndSizeTracking(path);
    }

    @Override
    protected void disposeAccessor(RandomAccessFileWithLengthAndSizeTracking fileAccessor) throws IOException {
      fileAccessor.close();
    }
  };

  private static final boolean useSingleFileDescriptor = SystemProperties.getBooleanProperty("idea.use.single.file.descriptor.for.persistent.hash.map", true);

  private static final FileAccessorCache<String, DataOutputStream> ourAppendersCache = new FileAccessorCache<String, DataOutputStream>(CACHE_PROTECTED_QUEUE_SIZE, CACHE_PROBATIONAL_QUEUE_SIZE) {
    @Override
    protected DataOutputStream createAccessor(String path) throws IOException {
      OutputStream out = useSingleFileDescriptor ? new OutputStreamOverRandomAccessFileCache(path):new FileOutputStream(path, true);
      return new DataOutputStream(new BufferedOutputStream(out));
    }

    @Override
    protected void disposeAccessor(DataOutputStream fileAccessor) throws IOException {
      if (!useSingleFileDescriptor) IOUtil.syncStream(fileAccessor);
      fileAccessor.close();
    }
  };

  private static final FileAccessorCache<String, RAReader> ourReadersCache = new FileAccessorCache<String, RAReader>(CACHE_PROTECTED_QUEUE_SIZE, CACHE_PROBATIONAL_QUEUE_SIZE) {
    @Override
    protected RAReader createAccessor(String path) throws IOException {
      return useSingleFileDescriptor ? new ReaderOverRandomAccessFileCache(path) : new FileReader(new File(path));
    }

    @Override
    protected void disposeAccessor(RAReader fileAccessor) {
      fileAccessor.dispose();
    }
  };

  private final CompressedAppendableFile myCompressedAppendableFile;

  public static final boolean COMPRESSION_ENABLED = SystemProperties.getBooleanProperty("idea.compression.enabled", true);

  public PersistentHashMapValueStorage(String path) throws IOException {
    myExceptionalIOCancellationCallback = CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.get();
    myReadOnly = CreationTimeOptions.READONLY.get() == Boolean.TRUE;
    myCompactChunksWithValueDeserialization = CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.get() == Boolean.TRUE;
    myPath = path;
    myFile = new File(path);

    myCompressedAppendableFile = CreationTimeOptions.DO_COMPRESSION.get() != Boolean.FALSE ? new MyCompressedAppendableFile() : null;
    if (myCompressedAppendableFile != null) {
      mySize = myCompressedAppendableFile.length();  // volatile write
    } else {
      mySize = myFile.length();  // volatile write
    }

    if (mySize == 0 && !myReadOnly) {
      appendBytes(new ByteSequence("Header Record For PersistentHashMapValueStorage".getBytes()), 0);

      // avoid corruption issue when disk fails to write first record synchronously or unexpected first write file increase (IDEA-106306),
      // code depends on correct value of mySize
      FileAccessorCache.Handle<DataOutputStream> streamCacheValue = ourAppendersCache.getIfCached(myPath);
      if (streamCacheValue != null) {
        try {
          IOUtil.syncStream(streamCacheValue.get());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        finally {
          streamCacheValue.release();
        }
      }

      long currentLength = myFile.length();
      if (currentLength > mySize) {  // if real file length (unexpectedly) increases
        Logger.getInstance(getClass().getName()).info("Avoided PSHM corruption due to write failure");
        mySize = currentLength;  // volatile write
      }
    }
  }

  public long appendBytes(ByteSequence data, long prevChunkAddress) throws IOException {
    return appendBytes(data.getBytes(), data.getOffset(), data.getLength(), prevChunkAddress);
  }

  public long appendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    assert allowedToCompactChunks();
    long result = mySize; // volatile read
    final FileAccessorCache.Handle<DataOutputStream> appender = myCompressedAppendableFile != null? null : ourAppendersCache.get(myPath);

    DataOutputStream dataOutputStream;
    try {
      if (myCompressedAppendableFile != null) {
        BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream(dataLength + 15);
        DataOutputStream testStream = new DataOutputStream(stream);
        saveData(data, offset, dataLength, prevChunkAddress, result, testStream);
        myCompressedAppendableFile.append(stream.getInternalBuffer(), stream.size());
        mySize += stream.size();  // volatile write
      } else {
        dataOutputStream = appender.get();
        dataOutputStream.resetWrittenBytesCount();

        saveData(data, offset, dataLength, prevChunkAddress, result, dataOutputStream);
        mySize += dataOutputStream.resetWrittenBytesCount();  // volatile write
      }
    }
    finally {
      if (appender != null) appender.release();
    }

    return result;
  }

  private void saveData(byte[] data,
                        int offset,
                        int dataLength,
                        long prevChunkAddress,
                        long result,
                        DataOutputStream dataOutputStream) throws IOException {
    DataInputOutputUtil.writeINT(dataOutputStream, dataLength);
    writePrevChunkAddress(prevChunkAddress, result, dataOutputStream);

    dataOutputStream.write(data, offset, dataLength);
  }

  private static final ThreadLocalCachedByteArray myBuffer = new ThreadLocalCachedByteArray();
  private final UnsyncByteArrayInputStream myBufferStreamWrapper = new UnsyncByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
  private final DataInputStream myBufferDataStreamWrapper = new DataInputStream(myBufferStreamWrapper);
  private static final int ourBufferLength = 4096;

  public long compactValues(List<PersistentHashMap.CompactionRecordInfo> infos, PersistentHashMapValueStorage storage) throws IOException {
    PriorityQueue<PersistentHashMap.CompactionRecordInfo> records = new PriorityQueue<PersistentHashMap.CompactionRecordInfo>(
      infos.size(), new Comparator<PersistentHashMap.CompactionRecordInfo>() {
        @Override
        public int compare(PersistentHashMap.CompactionRecordInfo info, PersistentHashMap.CompactionRecordInfo info2) {
          return Comparing.compare(info2.valueAddress,info.valueAddress );
        }
      }
    );

    records.addAll(infos);

    final int fileBufferLength = 256 * 1024;
    final int maxRecordHeader = 5 /* max length - variable int */ + 10 /* max long offset*/;
    final byte[] buffer = new byte[fileBufferLength + maxRecordHeader];
    byte[] recordBuffer = {};

    long lastReadOffset = mySize;
    long lastConsumedOffset = lastReadOffset;
    long allRecordsStart = 0;
    int fragments = 0;
    int newFragments = 0;
    int allRecordsLength = 0;
    byte[] stuffFromPreviousRecord = null;
    int bytesRead = (int)(mySize - (mySize / fileBufferLength) * fileBufferLength);
    long retained = 0;
    final long softMaxRetainedLimit = 10 * 1024* 1024;
    final int blockSizeToWriteWhenSoftMaxRetainedLimitIsHit = 1024;
    final long maxRetainedLimit = 100 * 1024* 1024;

    while(lastReadOffset != 0) {
      final long readStartOffset = lastReadOffset - bytesRead;
      myCompactionModeReader.get(readStartOffset, buffer, 0, bytesRead); // buffer contains [readStartOffset, readStartOffset + bytesRead)

      while(!records.isEmpty()) {
        final PersistentHashMap.CompactionRecordInfo info = records.peek();
        if (info.valueAddress >= readStartOffset) {
          if (info.valueAddress >= lastReadOffset) {
            throw new IOException("Value storage is corrupted: value file size:" + mySize + ", readStartOffset:"+ readStartOffset + ", record address:"+info.valueAddress+"; file: "+myPath);
          }
          // record start is inside our buffer

          final int recordStartInBuffer = (int) (info.valueAddress - readStartOffset);
          myBufferStreamWrapper.init(buffer, recordStartInBuffer, buffer.length);

          final long prevChunkAddress;
          int chunkSize;
          final int dataOffset;

          if (stuffFromPreviousRecord != null && (fileBufferLength - recordStartInBuffer) < maxRecordHeader) {
            // add additional bytes to read offset / size
            if (allRecordsStart != 0) {
              myCompactionModeReader.get(allRecordsStart, buffer, bytesRead, maxRecordHeader);
            } else {
              final int maxAdditionalBytes = Math.min(stuffFromPreviousRecord.length, maxRecordHeader);
              for(int i = 0; i < maxAdditionalBytes; ++i) {
                buffer[bytesRead + i] = stuffFromPreviousRecord[i];
              }
            }
          }

          int available = myBufferStreamWrapper.available();
          chunkSize = DataInputOutputUtil.readINT(myBufferDataStreamWrapper);
          prevChunkAddress = readPrevChunkAddress(info.valueAddress);
          dataOffset = available - myBufferStreamWrapper.available();

          byte[] b;
          if (info.value != null) {
            int defragmentedChunkSize = info.value.length + chunkSize;
            if (prevChunkAddress == 0) {
              if (defragmentedChunkSize >= recordBuffer.length) recordBuffer = new byte[defragmentedChunkSize];
              b = recordBuffer;
            } else {
              b = new byte[defragmentedChunkSize];
              retained += defragmentedChunkSize;
            }
            System.arraycopy(info.value, 0, b, chunkSize, info.value.length);
          } else {
            if (prevChunkAddress == 0) {
              if (chunkSize >= recordBuffer.length) recordBuffer = new byte[chunkSize];
              b = recordBuffer;
            } else {
              b = new byte[chunkSize];
              retained += chunkSize;
            }
          }

          final int chunkSizeOutOfBuffer = Math.min(chunkSize,
                                                    Math.max((int)(info.valueAddress + dataOffset + chunkSize - lastReadOffset), 0));
          if (chunkSizeOutOfBuffer > 0) {
            if (allRecordsStart != 0) {
              myCompactionModeReader.get(allRecordsStart, b, chunkSize - chunkSizeOutOfBuffer, chunkSizeOutOfBuffer);
            } else {
              int offsetInStuffFromPreviousRecord = Math.max((int)(info.valueAddress + dataOffset - lastReadOffset), 0);
              // stuffFromPreviousRecord starts from lastReadOffset
              System.arraycopy(stuffFromPreviousRecord, offsetInStuffFromPreviousRecord, b, chunkSize - chunkSizeOutOfBuffer, chunkSizeOutOfBuffer);
            }
          }

          stuffFromPreviousRecord = null;
          allRecordsStart = allRecordsLength = 0;

          lastConsumedOffset = info.valueAddress;
          checkPreconditions(b, chunkSize, 0);

          System.arraycopy(buffer, recordStartInBuffer + dataOffset, b, 0, chunkSize - chunkSizeOutOfBuffer);

          ++fragments;
          records.remove(info);
          if (info.value != null) {
            chunkSize += info.value.length;
            retained -= info.value.length;
            info.value = null;
          }

          if (prevChunkAddress == 0) {
            info.newValueAddress = storage.appendBytes(b, 0, chunkSize, info.newValueAddress);
            ++newFragments;
          } else {
            if (retained > softMaxRetainedLimit && b.length > blockSizeToWriteWhenSoftMaxRetainedLimitIsHit ||
                retained > maxRetainedLimit) {
              ++newFragments;
              info.newValueAddress = storage.appendBytes(b, 0, chunkSize, info.newValueAddress);
              info.value = null;
              retained -= b.length;
            } else {
              info.value = b;
            }
            info.valueAddress = prevChunkAddress;
            records.add(info);
          }
        } else {
          // [readStartOffset,lastConsumedOffset) is from previous segment
          if (stuffFromPreviousRecord == null) {
            stuffFromPreviousRecord = new byte[(int)(lastConsumedOffset - readStartOffset)];
            System.arraycopy(buffer, 0, stuffFromPreviousRecord, 0, stuffFromPreviousRecord.length);
          } else {
            allRecordsStart = readStartOffset;
            allRecordsLength += buffer.length;
          }
          break; // request next read
        }
      }

      lastReadOffset -= bytesRead;
      bytesRead = fileBufferLength;
    }

    return fragments | ((long)newFragments << 32);
  }

  public static class ReadResult {
    public final byte[] buffer;
    public final int chunksCount;

    public ReadResult(byte[] buffer, int chunksCount) {
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
   * Reads bytes pointed by tailChunkAddress into result passed, returns new address if linked list compactification have been performed
   */
  public ReadResult readBytes(long tailChunkAddress) throws IOException {
    forceAppender(myPath);

    checkCancellation();
    long startedTime = ourDumpChunkRemovalTime ? System.nanoTime() : 0;
    long chunk = tailChunkAddress;
    int chunkCount = 0;

    byte[] result = null;
    RAReader reader = myCompactionModeReader;
    FileAccessorCache.Handle<RAReader> readerHandle = null;
    if (reader == null) {
      readerHandle = myCompressedAppendableFile != null ? null : ourReadersCache.get(myPath);
      reader = myCompressedAppendableFile != null ? null : readerHandle.get();
    }

    try {
      while (chunk != 0) {
        if (chunk < 0 || chunk > mySize) throw new PersistentEnumeratorBase.CorruptedException(myFile);

        byte[] buffer = myBuffer.getBuffer(ourBufferLength);
        int len = (int)Math.min(ourBufferLength, mySize - chunk);

        if (myCompressedAppendableFile != null) {
          DataInputStream stream = myCompressedAppendableFile.getStream(chunk);
          stream.readFully(buffer, 0, len);
          stream.close();
        } else {
          reader.get(chunk, buffer, 0, len);
        }
        myBufferStreamWrapper.init(buffer, 0, len);

        final int chunkSize = DataInputOutputUtil.readINT(myBufferDataStreamWrapper);
        if (chunkSize < 0) {
          throw new IOException("Value storage corrupted: negative chunk size: "+chunkSize);
        }
        final long prevChunkAddress = readPrevChunkAddress(chunk);
        final int headerOffset = len - myBufferStreamWrapper.available();

        byte[] b = new byte[(result != null ? result.length:0) + chunkSize];
        if (result != null) System.arraycopy(result, 0, b, b.length - result.length, result.length);
        result = b;

        checkPreconditions(result, chunkSize, 0);
        if (chunkSize < ourBufferLength - headerOffset) {
          System.arraycopy(buffer, headerOffset, result, 0, chunkSize);
        } else {
          if (myCompressedAppendableFile != null) {
            DataInputStream stream = myCompressedAppendableFile.getStream(chunk + headerOffset);
            stream.readFully(result, 0, chunkSize);
            stream.close();
          } else {
            reader.get(chunk + headerOffset, result, 0, chunkSize);
          }
        }

        if (prevChunkAddress >= chunk) throw new PersistentEnumeratorBase.CorruptedException(myFile);

        chunk = prevChunkAddress;
        chunkCount++;

        if (prevChunkAddress != 0) checkCancellation();
        if (result.length > mySize && myCompressedAppendableFile == null) {
          throw new PersistentEnumeratorBase.CorruptedException(myFile);
        }
      }
    } catch (OutOfMemoryError error) {
      result = null;
      throw new PersistentEnumeratorBase.CorruptedException(myFile);
    }
    finally {
      if (readerHandle != null) {
        readerHandle.release();
      }
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
    return !myCompactionMode && !myReadOnly;
  }

  boolean performChunksCompaction(int chunksCount, int chunksBytesSize) {
    return chunksCount > 1 && allowedToCompactChunks();
  }

  long compactChunks(PersistentHashMap.ValueDataAppender appender, ReadResult result) throws IOException {
    checkCancellation();
    long startedTime = ourDumpChunkRemovalTime ? System.nanoTime() : 0;
    long newValueOffset;

    if (myCompactChunksWithValueDeserialization) {
      final BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream(result.buffer.length);
      DataOutputStream testStream = new DataOutputStream(stream);
      appender.append(testStream);
      newValueOffset = appendBytes(stream.getInternalBuffer(), 0, stream.size(), 0);
      myChunksBytesAfterRemoval += stream.size();
    } else {
      newValueOffset = appendBytes(new ByteSequence(result.buffer), 0);
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
  protected void checkCancellation() {
    if (myExceptionalIOCancellationCallback != null) myExceptionalIOCancellationCallback.checkCancellation();
  }

  private long readPrevChunkAddress(long chunk) throws IOException {
    final long prevOffsetDiff = DataInputOutputUtil.readLONG(myBufferDataStreamWrapper);
    if(prevOffsetDiff >= chunk) {
      throw new IOException("readPrevChunkAddress:" + chunk + "," + prevOffsetDiff + "," + mySize + "," + myFile);
    }
    return prevOffsetDiff != 0 ? chunk - prevOffsetDiff : 0;
  }

  private void writePrevChunkAddress(long prevChunkAddress, long currentChunkAddress, DataOutputStream dataOutputStream) throws IOException {
    if(currentChunkAddress < prevChunkAddress) {
      throw new IOException("writePrevChunkAddress:" + currentChunkAddress + "," + prevChunkAddress + "," + myFile);
    }
    long diff = currentChunkAddress - prevChunkAddress;
    DataInputOutputUtil.writeLONG(dataOutputStream, prevChunkAddress == 0 ? 0 : diff);
  }

  public long getSize() {
    return mySize;
  }

  private static void checkPreconditions(final byte[] result, final int chunkSize, final int off) throws IOException {
    if (chunkSize < 0) {
      throw new IOException("Value storage corrupted: negative chunk size");
    }
    if (off < 0) {
      throw new IOException("Value storage corrupted: negative offset");
    }
    if (chunkSize > result.length - off) {
      throw new IOException("Value storage corrupted");
    }
  }

  public void force() {
    if (myReadOnly) return;
    if (myCompressedAppendableFile != null) {
      myCompressedAppendableFile.force();
    }
    if (mySize < 0) assert false;  // volatile read
    forceAppender(myPath);
  }

  private static void forceAppender(String path) {
    final FileAccessorCache.Handle<DataOutputStream> cached = ourAppendersCache.getIfCached(path);
    if (cached != null) {
      try {
        cached.get().flush();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      finally {
        cached.release();
      }
    }
  }

  public void dispose() {
    try {
      if (myCompressedAppendableFile != null) myCompressedAppendableFile.dispose();
    } finally {
      if (mySize < 0) assert false; // volatile read
      ourReadersCache.remove(myPath);
      ourAppendersCache.remove(myPath);

      ourRandomAccessFileCache.remove(myPath);

      if (myCompactionModeReader != null) {
        myCompactionModeReader.dispose();
        myCompactionModeReader = null;
      }
    }
  }

  public void switchToCompactionMode() {
    ourReadersCache.remove(myPath);

    ourRandomAccessFileCache.remove(myPath);
    // in compaction mode use faster reader
    if (myCompressedAppendableFile != null) {
      myCompactionModeReader = new RAReader() {
        @Override
        public void get(long addr, byte[] dst, int off, int len) throws IOException {
          DataInputStream stream = myCompressedAppendableFile.getStream(addr);
          stream.readFully(dst, off, len);
          stream.close();
        }

        @Override
        public void dispose() {
        }
      };
    } else {
      myCompactionModeReader = new FileReader(myFile);
    }

    myCompactionMode = true;
  }

  public static PersistentHashMapValueStorage create(final String path, boolean readOnly) throws IOException {
    if (readOnly) CreationTimeOptions.READONLY.set(Boolean.TRUE);
    try {
      return new PersistentHashMapValueStorage(path);
    } finally {
      if (readOnly) CreationTimeOptions.READONLY.set(null);
    }
  }

  private interface RAReader {
    void get(long addr, byte[] dst, int off, int len) throws IOException;
    void dispose();
  }

  private static class ReaderOverRandomAccessFileCache implements RAReader {
    private String myPath;

    private ReaderOverRandomAccessFileCache(String path) {
      myPath = path;
    }

    @Override
    public void get(final long addr, final byte[] dst, final int off, final int len) throws IOException {
      FileAccessorCache.Handle<RandomAccessFileWithLengthAndSizeTracking> fileAccessor = ourRandomAccessFileCache.get(myPath);

      try {
        RandomAccessFileWithLengthAndSizeTracking file = fileAccessor.get();
        file.seek(addr);
        file.read(dst, off, len);
      } finally {
        fileAccessor.release();
      }
    }

    @Override
    public void dispose() {
    }
  }

  private static class FileReader implements RAReader {
    private final RandomAccessFile myFile;

    private FileReader(File file) {
      try {
        myFile = new RandomAccessFile(file, "r");
      }
      catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void get(final long addr, final byte[] dst, final int off, final int len) throws IOException {
      myFile.seek(addr);
      myFile.read(dst, off, len);
    }

    @Override
    public void dispose() {
      try {
        myFile.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class OutputStreamOverRandomAccessFileCache extends OutputStream {
    private final String myPath;

    public OutputStreamOverRandomAccessFileCache(String path) throws IOException {
      myPath = path;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      FileAccessorCache.Handle<RandomAccessFileWithLengthAndSizeTracking> fileAccessor = ourRandomAccessFileCache.get(myPath);
      RandomAccessFileWithLengthAndSizeTracking file = fileAccessor.get();

      try {
        file.seek(file.length());
        file.write(b, off, len);
      }
      finally {
        fileAccessor.release();
      }
    }

    @Override
    public void write(int b) throws IOException {
      byte[] r = {(byte)(b & 0xFF)};
      write(r);
    }
  }

  private class MyCompressedAppendableFile extends CompressedAppendableFile {

    public MyCompressedAppendableFile() {
      super(PersistentHashMapValueStorage.this.myFile);
    }

    @NotNull
    @Override
    protected InputStream getChunkInputStream(File appendFile, long offset, int pageSize) throws IOException {
      forceAppender(myPath);
      FileAccessorCache.Handle<RAReader> fileAccessor = ourReadersCache.get(myPath);

      try {
        byte[] bytes = new byte[pageSize];
        fileAccessor.get().get(offset, bytes, 0, pageSize);
        return new ByteArrayInputStream(bytes);
      } finally {
        fileAccessor.release();
      }
    }

    @Override
    protected void saveChunk(BufferExposingByteArrayOutputStream compressedChunk, long endOfFileOffset) throws IOException {
      FileAccessorCache.Handle<DataOutputStream> streamCacheValue = ourAppendersCache.get(myPath);
      try {
        streamCacheValue.get().write(compressedChunk.getInternalBuffer(), 0, compressedChunk.size());
      } finally {
        streamCacheValue.release();
      }

      streamCacheValue = ourAppendersCache.get(myPath + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
      try {
        DataInputOutputUtil.writeINT(streamCacheValue.get(), compressedChunk.size());
      } finally {
        streamCacheValue.release();
      }
    }

    @NotNull
    @Override
    protected File getChunksFile() {
      return myFile;
    }

    @Override
    protected File getChunkLengthFile() {
      return new File(myFile.getPath() + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
    }

    @Override
    public synchronized void force() {
      super.force();
      forceAppender(myPath + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
    }

    @Override
    public synchronized void dispose() {
      super.dispose();

      ourAppendersCache.remove(myPath + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
      ourRandomAccessFileCache.remove(myPath + INCOMPLETE_CHUNK_LENGTH_FILE_EXTENSION);
    }
  }

  @TestOnly
  public boolean isReadOnly() {
    return myReadOnly;
  }
}
