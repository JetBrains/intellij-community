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
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.*;

public class PersistentHashMapValueStorage {
  @Nullable
  private RAReader myCompactionModeReader = null;
  private volatile long mySize;
  private final File myFile;
  private final String myPath;

  private final CreationTimeOptions myOptions;
  
  private boolean myCompactionMode = false;

  private static final int CACHE_PROTECTED_QUEUE_SIZE = 10;
  private static final int CACHE_PROBATIONAL_QUEUE_SIZE = 20;
  private static final long MAX_RETAINED_LIMIT_WHEN_COMPACTING = 100 * 1024 * 1024;

  public static class CreationTimeOptions {
    public static final ThreadLocal<ExceptionalIOCancellationCallback> EXCEPTIONAL_IO_CANCELLATION = new ThreadLocal<ExceptionalIOCancellationCallback>();
    public static final ThreadLocal<Boolean> READONLY = new ThreadLocal<Boolean>();
    public static final ThreadLocal<Boolean> COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION = new ThreadLocal<Boolean>();
    public static final ThreadLocal<Boolean> HAS_NO_CHUNKS = new ThreadLocal<Boolean>();
    
    public static final ThreadLocal<Boolean> DO_COMPRESSION = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.valueOf(COMPRESSION_ENABLED);
      }
    };

    private final ExceptionalIOCancellationCallback myExceptionalIOCancellationCallback;
    private final boolean myReadOnly;
    private final boolean myCompactChunksWithValueDeserialization;
    private final boolean myHasNoChunks;
    private final boolean myDoCompression;

    private CreationTimeOptions(ExceptionalIOCancellationCallback callback,
                                boolean readOnly,
                                boolean compactChunksWithValueDeserialization,
                                boolean hasNoChunks,
                                boolean doCompression) {
      myExceptionalIOCancellationCallback = callback;
      myReadOnly = readOnly;
      myCompactChunksWithValueDeserialization = compactChunksWithValueDeserialization;
      myHasNoChunks = hasNoChunks;
      myDoCompression = doCompression;
    }

    int getVersion() {
      return (myHasNoChunks ? 10 : 0) * 31 + (myDoCompression ? 0x13:0);
    }

    CreationTimeOptions setReadOnly() {
      return new CreationTimeOptions(
        myExceptionalIOCancellationCallback,
        true,
        myCompactChunksWithValueDeserialization,
        myHasNoChunks,
        myDoCompression
      );
    }

    static CreationTimeOptions threadLocalOptions() {
      return new CreationTimeOptions(
        EXCEPTIONAL_IO_CANCELLATION.get(),
        READONLY.get() == Boolean.TRUE,
        COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.get() == Boolean.TRUE,
        CreationTimeOptions.HAS_NO_CHUNKS.get() == Boolean.TRUE,
        DO_COMPRESSION.get() == Boolean.TRUE);
    }
  }
  
  public interface ExceptionalIOCancellationCallback {
    void checkCancellation();
  }

  CreationTimeOptions getOptions() {
    return myOptions;
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

  PersistentHashMapValueStorage(String path) throws IOException {
    this(path, CreationTimeOptions.threadLocalOptions());
  }

  PersistentHashMapValueStorage(String path, CreationTimeOptions options) throws IOException {
    myPath = path;
    myFile = new File(path);
    myOptions = options;

    myCompressedAppendableFile = myOptions.myDoCompression ? new MyCompressedAppendableFile() : null;
    if (myCompressedAppendableFile != null) {
      mySize = myCompressedAppendableFile.length();  // volatile write
    } else {
      mySize = myFile.length();  // volatile write
    }
  }

  public long appendBytes(ByteArraySequence data, long prevChunkAddress) throws IOException {
    return appendBytes(data.getBytes(), data.getOffset(), data.getLength(), prevChunkAddress);
  }

  public long appendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    if (mySize == 0) {
      byte[] bytes = "Header Record For PersistentHashMapValueStorage".getBytes();
      doAppendBytes(bytes, 0, bytes.length, 0);

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
        Logger.getInstance(getClass().getName()).info("Avoided PSHM corruption due to write failure:" + myPath);
        mySize = currentLength;  // volatile write
      }
    }

    return doAppendBytes(data, offset, dataLength, prevChunkAddress);
  }
  
  void checkAppendsAllowed(int previouslyAccumulatedChunkSize) {
    if (previouslyAccumulatedChunkSize != 0) {
      assert !myOptions.myHasNoChunks;
    }
  }

  protected long doAppendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    assert allowedToCompactChunks();
    if (prevChunkAddress != 0) {
      assert !myOptions.myHasNoChunks;
    }
    long result = mySize; // volatile read
    final FileAccessorCache.Handle<DataOutputStream> appender = myCompressedAppendableFile != null ? null : ourAppendersCache.get(myPath);

    DataOutputStream dataOutputStream;
    try {
      if (myCompressedAppendableFile != null) {
        BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream(15);
        DataOutputStream testStream = new DataOutputStream(stream);
        saveHeader(dataLength, prevChunkAddress, result, testStream);
        myCompressedAppendableFile.append(stream.getInternalBuffer(), stream.size());
        myCompressedAppendableFile.append(data, offset, dataLength);
        mySize += stream.size() + dataLength;  // volatile write
      } else {
        dataOutputStream = appender.get();
        dataOutputStream.resetWrittenBytesCount();

        saveHeader(dataLength, prevChunkAddress, result, dataOutputStream);
        dataOutputStream.write(data, offset, dataLength);
        mySize += dataOutputStream.resetWrittenBytesCount();  // volatile write
      }
    }
    finally {
      if (appender != null) appender.release();
    }

    return result;
  }

  private void saveHeader(int dataLength,
                          long prevChunkAddress,
                          long result,
                          DataOutputStream dataOutputStream) throws IOException {
    DataInputOutputUtil.writeINT(dataOutputStream, dataLength);
    if (!myOptions.myHasNoChunks) {
      if(result < prevChunkAddress) {
        throw new IOException("writePrevChunkAddress:" + result + "," + prevChunkAddress + "," + myFile);
      }
      long diff = result - prevChunkAddress;
      DataInputOutputUtil.writeLONG(dataOutputStream, prevChunkAddress == 0 ? 0 : diff);
    }
  }

  private static final ThreadLocalCachedByteArray myBuffer = new ThreadLocalCachedByteArray();
  private final UnsyncByteArrayInputStream myBufferStreamWrapper = new UnsyncByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
  private final DataInputStream myBufferDataStreamWrapper = new DataInputStream(myBufferStreamWrapper);
  private static final int ourBufferLength = 1024;

  private long compactValuesWithoutChunks(List<PersistentHashMap.CompactionRecordInfo> infos, PersistentHashMapValueStorage storage) throws IOException {
    //infos = new ArrayList<PersistentHashMap.CompactionRecordInfo>(infos);
    Collections.sort(infos, new Comparator<PersistentHashMap.CompactionRecordInfo>() {
      @Override
      public int compare(PersistentHashMap.CompactionRecordInfo info, PersistentHashMap.CompactionRecordInfo info2) {
        return Comparing.compare(info.valueAddress, info2.valueAddress);
      }
    });

    final int fileBufferLength = 256 * 1024;
    final byte[] buffer = new byte[fileBufferLength];

    int fragments = 0;
    int newFragments = 0;

    byte[] outputBuffer = new byte[4096];

    long readStartOffset = -1;
    int bytesRead = -1;

    for(PersistentHashMap.CompactionRecordInfo info:infos) {
      int recordStartInBuffer = (int) (info.valueAddress - readStartOffset);

      if (recordStartInBuffer + 5 > fileBufferLength || readStartOffset == -1) {
        readStartOffset = info.valueAddress;
        long remainingBytes = readStartOffset != -1 ? mySize - readStartOffset : mySize;
        bytesRead = (remainingBytes < fileBufferLength) ? (int)remainingBytes : fileBufferLength;

        myCompactionModeReader.get(readStartOffset, buffer, 0, bytesRead); // buffer contains [readStartOffset, readStartOffset + bytesRead)
        recordStartInBuffer = (int) (info.valueAddress - readStartOffset);
      }

      myBufferStreamWrapper.init(buffer, recordStartInBuffer, buffer.length);
      int available = myBufferStreamWrapper.available();
      int chunkSize = DataInputOutputUtil.readINT(myBufferDataStreamWrapper);
      long prevChunkAddress = readPrevChunkAddress(info.valueAddress);
      assert prevChunkAddress == 0;
      int dataOffset = available - myBufferStreamWrapper.available() + recordStartInBuffer;

      if (chunkSize >= outputBuffer.length) {
        outputBuffer = new byte[((chunkSize / 4096) + 1) * 4096];
      }

      // dataOffset .. dataOffset + chunkSize
      int bytesFitInBuffer = Math.min(chunkSize, fileBufferLength - dataOffset);
      System.arraycopy(buffer, dataOffset, outputBuffer, 0, bytesFitInBuffer);

      while(bytesFitInBuffer != chunkSize) {
        readStartOffset += bytesRead;

        long remainingBytes = mySize - readStartOffset;
        bytesRead = (remainingBytes < fileBufferLength) ? (int)remainingBytes : fileBufferLength;

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
  
  public long compactValues(List<PersistentHashMap.CompactionRecordInfo> infos, PersistentHashMapValueStorage storage) throws IOException {
    if (myOptions.myHasNoChunks) {
      return compactValuesWithoutChunks(infos, storage);
    }
    
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
                retained > MAX_RETAINED_LIMIT_WHEN_COMPACTING) {
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

        if (prevChunkAddress != 0) {
          checkCancellation();
          assert !myOptions.myHasNoChunks;
        }
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
    return !myCompactionMode && !myOptions.myReadOnly;
  }

  boolean performChunksCompaction(int chunksCount, int chunksBytesSize) {
    return chunksCount > 1 && allowedToCompactChunks();
  }

  long compactChunks(PersistentHashMap.ValueDataAppender appender, ReadResult result) throws IOException {
    checkCancellation();
    long startedTime = ourDumpChunkRemovalTime ? System.nanoTime() : 0;
    long newValueOffset;

    if (myOptions.myCompactChunksWithValueDeserialization) {
      final BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream(result.buffer.length);
      DataOutputStream testStream = new DataOutputStream(stream);
      appender.append(testStream);
      newValueOffset = appendBytes(stream.getInternalBuffer(), 0, stream.size(), 0);
      myChunksBytesAfterRemoval += stream.size();
    } else {
      newValueOffset = appendBytes(new ByteArraySequence(result.buffer), 0);
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
    if (myOptions.myExceptionalIOCancellationCallback != null) myOptions.myExceptionalIOCancellationCallback.checkCancellation();
  }

  private long readPrevChunkAddress(long chunk) throws IOException {
    if (myOptions.myHasNoChunks) return 0;
    final long prevOffsetDiff = DataInputOutputUtil.readLONG(myBufferDataStreamWrapper);
    if(prevOffsetDiff >= chunk) {
      throw new IOException("readPrevChunkAddress:" + chunk + "," + prevOffsetDiff + "," + mySize + "," + myFile);
    }
    return prevOffsetDiff != 0 ? chunk - prevOffsetDiff : 0;
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
    if (myOptions.myReadOnly) return;
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

  public static PersistentHashMapValueStorage create(final String path, CreationTimeOptions options) throws IOException {
    return new PersistentHashMapValueStorage(path, options);
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
    return myOptions.myReadOnly;
  }
}
