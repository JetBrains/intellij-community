/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class PersistentHashMapValueStorage {
  @Nullable
  private RAReader myCompactionModeReader = null;
  private volatile long mySize;
  private final File myFile;
  private final String myPath;
  private boolean myCompactionMode = false;

  private static final int CACHE_PROTECTED_QUEUE_SIZE = 10;
  private static final int CACHE_PROBATIONAL_QUEUE_SIZE = 20;

  private static final FileAccessorCache<DataOutputStream> ourAppendersCache = new FileAccessorCache<DataOutputStream>(CACHE_PROTECTED_QUEUE_SIZE, CACHE_PROBATIONAL_QUEUE_SIZE) {
    @Override
    @NotNull
    public CacheValue<DataOutputStream> createValue(String path) {
      try {
        return new CachedAppender(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path, true))));
      }
      catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  private static final FileAccessorCache<RAReader> ourReadersCache = new FileAccessorCache<RAReader>(CACHE_PROTECTED_QUEUE_SIZE, CACHE_PROBATIONAL_QUEUE_SIZE) {
    @Override
    @NotNull
    public CacheValue<RAReader> createValue(String path) {
      return new CachedReader(new FileReader(new File(path)));
    }
  };

  public PersistentHashMapValueStorage(String path) throws IOException {
    myPath = path;
    myFile = new File(path);
    mySize = myFile.length();  // volatile write

    if (mySize == 0) {
      appendBytes(new ByteSequence("Header Record For PersistentHashMapValueStorage".getBytes()), 0);
      // avoid corruption issue when disk fails to write first record synchronously, code depends on correct value of mySize (IDEA-106306)
      CacheValue<DataOutputStream> streamCacheValue = ourAppendersCache.getIfCached(myPath);
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
      if (currentLength != mySize) Logger.getInstance(getClass().getName()).info("Avoided PSHM corruption due to write failure");
      mySize = currentLength;  // volatile write
    }
  }

  public long appendBytes(ByteSequence data, long prevChunkAddress) throws IOException {
    return appendBytes(data.getBytes(), data.getOffset(), data.getLength(), prevChunkAddress);
  }

  public long appendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    assert !myCompactionMode;
    long result = mySize; // volatile read
    final CacheValue<DataOutputStream> appender = ourAppendersCache.get(myPath);

    DataOutputStream dataOutputStream;
    try {
      dataOutputStream = appender.get();
      dataOutputStream.resetWrittenBytesCount();

      DataInputOutputUtil.writeINT(dataOutputStream, dataLength);
      writePrevChunkAddress(prevChunkAddress, result, dataOutputStream);

      dataOutputStream.write(data, offset, dataLength);
      mySize += dataOutputStream.resetWrittenBytesCount();  // volatile write
    }
    finally {
      appender.release();
    }

    return result;
  }

  private final byte[] myBuffer = new byte[1024];
  private final UnsyncByteArrayInputStream myBufferStreamWrapper = new UnsyncByteArrayInputStream(myBuffer);
  private final DataInputStream myBufferDataStreamWrapper = new DataInputStream(myBufferStreamWrapper);

  public long compactValues(List<PersistentHashMap.CompactionRecordInfo> infos, PersistentHashMapValueStorage storage) throws IOException {
    PriorityQueue<PersistentHashMap.CompactionRecordInfo> records = new PriorityQueue<PersistentHashMap.CompactionRecordInfo>(
      infos.size(), new Comparator<PersistentHashMap.CompactionRecordInfo>() {
        @Override
        public int compare(PersistentHashMap.CompactionRecordInfo info, PersistentHashMap.CompactionRecordInfo info2) {
          long i = info.valueAddress - info2.valueAddress;
          return i > 0 ? -1 : i < 0 ? 1 : 0;
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
            throw new IOException("Value storage is corrupted: value file size:" + mySize + ", readStartOffset:"+ readStartOffset + ", record address:"+info.valueAddress);
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
    public final long offset;
    public final byte[] buffer;

    public ReadResult(long offset, byte[] buffer) {
      this.offset = offset;
      this.buffer = buffer;
    }
  }

  /**
   * Reads bytes pointed by tailChunkAddress into result passed, returns new address if linked list compactification have been performed
   */
  public ReadResult readBytes(long tailChunkAddress) throws IOException {
    force();

    long chunk = tailChunkAddress;
    int chunkCount = 0;

    byte[] result = null;
    RAReader reader = myCompactionModeReader;
    CacheValue<RAReader> readerHandle = null;
    if (reader == null) {
      readerHandle = ourReadersCache.get(myPath);
      reader = readerHandle.get();
    }

    try {
      while (chunk != 0) {
        if (chunk < 0 || chunk > mySize) throw new PersistentEnumeratorBase.CorruptedException(myFile);
        int len = (int)Math.min(myBuffer.length, mySize - chunk);
        reader.get(chunk, myBuffer, 0, len);
        myBufferStreamWrapper.init(myBuffer, 0, len);

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
        if (chunkSize < myBuffer.length - headerOffset) {
          System.arraycopy(myBuffer, headerOffset, result, 0, chunkSize);
        } else {
          reader.get(chunk + headerOffset, result, 0, chunkSize);
        }

        if (prevChunkAddress >= chunk) throw new PersistentEnumeratorBase.CorruptedException(myFile);

        chunk = prevChunkAddress;
        chunkCount++;
        if (result.length > mySize) {
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

    if (chunkCount > 1 && !myCompactionMode) {
      long l = appendBytes(new ByteSequence(result), 0);
      return new ReadResult(l, result);
    }

    return new ReadResult(tailChunkAddress, result);
  }

  private long readPrevChunkAddress(long chunk) throws IOException {
    final int prevOffsetDiff = DataInputOutputUtil.readINT(myBufferDataStreamWrapper);
    if (prevOffsetDiff < 0) {
      return chunk - (-prevOffsetDiff | ((long)DataInputOutputUtil.readINT(myBufferDataStreamWrapper) << 32));
    }
    assert prevOffsetDiff < chunk;
    return prevOffsetDiff != 0 ? chunk - prevOffsetDiff : 0;
  }

  private static void writePrevChunkAddress(long prevChunkAddress, long currentChunkAddress, DataOutputStream dataOutputStream) throws IOException {
    long diff = currentChunkAddress - prevChunkAddress;

    if (diff < Integer.MAX_VALUE || prevChunkAddress == 0)
      DataInputOutputUtil.writeINT(dataOutputStream, prevChunkAddress == 0 ? 0 : (int)diff);
    else {
      DataInputOutputUtil.writeINT(dataOutputStream, -(int)diff);
      DataInputOutputUtil.writeINT(dataOutputStream, (int)(diff >>> 32));
    }
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
    if (mySize < 0) assert false;  // volatile read
    final CacheValue<DataOutputStream> cached = ourAppendersCache.getIfCached(myPath);
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
    if (mySize < 0) assert false; // volatile read
    ourReadersCache.remove(myPath);
    ourAppendersCache.remove(myPath);

    if (myCompactionModeReader != null) {
      myCompactionModeReader.dispose();
      myCompactionModeReader = null;
    }
  }

  public void switchToCompactionMode() {
    ourReadersCache.remove(myPath);
    // in compaction mode use faster reader
    myCompactionModeReader = new FileReader(myFile);
    myCompactionMode = true;
  }

  public static PersistentHashMapValueStorage create(final String path) throws IOException {
    return new PersistentHashMapValueStorage(path);
  }

  private interface RAReader {
    void get(long addr, byte[] dst, int off, int len) throws IOException;
    void dispose();
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

  private abstract static class FileAccessorCache<T> extends SLRUCache<String, CacheValue<T>> {
    private final Object myLock = new Object();
    private FileAccessorCache(int protectedQueueSize, int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

    @Override
    @NotNull
    public final CacheValue<T> get(String key) {
      synchronized (myLock) {
        final CacheValue<T> value = super.get(key);
        value.allocate();
        return value;
      }
    }

    @Override
    public CacheValue<T> getIfCached(String key) {
      synchronized (myLock) {
        final CacheValue<T> value = super.getIfCached(key);
        if (value != null) {
          value.allocate();
        }
        return value;
      }
    }

    @Override
    public boolean remove(String key) {
      synchronized (myLock) {
        return super.remove(key);
      }
    }

    @Override
    protected final void onDropFromCache(String key, CacheValue<T> value) {
      value.release();
    }
  }

  private static class CachedAppender extends CacheValue<DataOutputStream> {
    private CachedAppender(DataOutputStream os) {
      super(os);
    }

    @Override
    protected void disposeAccessor(DataOutputStream os) {
      try {
        os.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class CachedReader extends CacheValue<RAReader> {
    private CachedReader(RAReader reader) {
      super(reader);
    }

    @Override
    protected void disposeAccessor(RAReader reader) {
      reader.dispose();
    }
  }

  private abstract static class CacheValue<T> {
    private final T myFileAccessor;
    private final AtomicInteger myRefCount = new AtomicInteger(1);

    private CacheValue(T fileAccessor) {
      myFileAccessor = fileAccessor;
    }

    public final void allocate() {
      myRefCount.incrementAndGet();
    }

    public final void release() {
      if (myRefCount.decrementAndGet() == 0) {
        disposeAccessor(myFileAccessor);
      }
    }

    public T get() {
      return myFileAccessor;
    }

    protected abstract void disposeAccessor(T accesor);
  }
}
