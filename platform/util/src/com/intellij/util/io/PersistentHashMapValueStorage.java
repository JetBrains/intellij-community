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

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PersistentHashMapValueStorage {
  @Nullable
  private RAReader myCompactionModeReader = null;
  private long mySize;
  private final File myFile;
  private final String myPath;
  private boolean myCompactionMode = false;

  private static final int CACHE_PROTECTED_QUEUE_SIZE = 10;
  private static final int CACHE_PROBATIONAL_QUEUE_SIZE = 20;

  private static final FileAccessorCache<DataOutputStream> ourAppendersCache = new FileAccessorCache<DataOutputStream>(CACHE_PROTECTED_QUEUE_SIZE, CACHE_PROBATIONAL_QUEUE_SIZE) {
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
    @NotNull
    public CacheValue<RAReader> createValue(String path) {
      return new CachedReader(new FileReader(new File(path)));
    }
  };

  public PersistentHashMapValueStorage(String path) throws IOException {
    myPath = path;
    myFile = new File(path);
    mySize = myFile.length();

    if (mySize == 0) {
      appendBytes(new ByteSequence("Header Record For PersistentHashMapValuStorage".getBytes()), 0);
    }
  }

  private long smallWrites;
  private int smallWritesCount;
  private long largeWrites;
  private int largeWritesCount;
  private int requests;

  private static final int POSITIVE_VALUE_SHIFT = 1;
  private static final int BYTE_LENGTH_INT_ADDRESS = 1 + 4;
  private static final int INT_LENGTH_LONG_ADDRESS = 4 + 8;

  public long appendBytes(ByteSequence data, long prevChunkAddress) throws IOException {
    return appendBytes(data.getBytes(), data.getOffset(), data.getLength(), prevChunkAddress);
  }

  public long appendBytes(byte[] data, int offset, int dataLength, long prevChunkAddress) throws IOException {
    assert !myCompactionMode;
    long result = mySize;
    final CacheValue<DataOutputStream> appender = ourAppendersCache.get(myPath);
    int serviceFieldsSizeIncrease;

    try {
      DataOutputStream dataOutputStream = appender.get();
      ++requests;
      
      if (dataLength + POSITIVE_VALUE_SHIFT < 0x80 && prevChunkAddress < Integer.MAX_VALUE) {
        ++smallWritesCount;
        smallWrites += dataLength;
        dataOutputStream.write(-dataLength - POSITIVE_VALUE_SHIFT);
        dataOutputStream.writeInt((int)prevChunkAddress);
        serviceFieldsSizeIncrease = BYTE_LENGTH_INT_ADDRESS;
      } else {
        ++largeWritesCount;
        largeWrites += dataLength;
        dataOutputStream.writeInt(dataLength);
        dataOutputStream.writeLong(prevChunkAddress);
        serviceFieldsSizeIncrease = INT_LENGTH_LONG_ADDRESS;
      }
      dataOutputStream.write(data, offset, dataLength);
      if (requests % IOStatistics.KEYS_FACTOR == 0 && IOStatistics.DEBUG) {
        IOStatistics.dump("Small writes:"+smallWritesCount +", bytes:"+smallWrites + ", largeWrites:"+largeWritesCount
                          + ", bytes:"+largeWrites+", total:"+requests + "@"+myFile.getPath());
      }
    }
    finally {
      appender.release();
    }
    mySize += dataLength + serviceFieldsSizeIncrease;

    return result;
  }

  private final byte[] myBuffer = new byte[1024];

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

        final int sizePart = myBuffer[0];
        final long prevChunkAddress;
        final int chunkSize;

        if (sizePart < 0) {
          chunkSize = -sizePart - POSITIVE_VALUE_SHIFT;
          prevChunkAddress = Bits.getInt(myBuffer, 1);
          byte[] b = new byte[(result != null ? result.length:0) + chunkSize];
          if (result != null) System.arraycopy(result, 0, b, b.length - result.length, result.length);
          result = b;

          checkPreconditions(result, chunkSize, 0);
          System.arraycopy(myBuffer, BYTE_LENGTH_INT_ADDRESS, result, 0, chunkSize);
        } else {
          chunkSize = Bits.getInt(myBuffer, 0);
          prevChunkAddress = Bits.getLong(myBuffer, 4);
          byte[] b = new byte[(result != null ? result.length:0) + chunkSize];
          if (result != null) System.arraycopy(result, 0, b, b.length - result.length, result.length);
          result = b;

          if (chunkSize < myBuffer.length - INT_LENGTH_LONG_ADDRESS) {
            System.arraycopy(myBuffer, INT_LENGTH_LONG_ADDRESS, result, 0, chunkSize);
          } else {
            reader.get(chunk + INT_LENGTH_LONG_ADDRESS, result, 0, chunkSize);
          }
        }

        chunk = prevChunkAddress;
        chunkCount++;
        if (result.length > mySize) {
          throw new PersistentEnumeratorBase.CorruptedException(myFile);
        }
      }
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

    public void get(final long addr, final byte[] dst, final int off, final int len) throws IOException {
      myFile.seek(addr);
      myFile.read(dst, off, len);
    }

    public void dispose() {
      try {
        myFile.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static abstract class FileAccessorCache<T> extends SLRUCache<String, CacheValue<T>> {
    private final Object myLock = new Object();
    private FileAccessorCache(int protectedQueueSize, int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

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

    public boolean remove(String key) {
      synchronized (myLock) {
        return super.remove(key);
      }
    }

    protected final void onDropFromCache(String key, CacheValue<T> value) {
      value.release();
    }
  }

  private static class CachedAppender extends CacheValue<DataOutputStream> {
    private CachedAppender(DataOutputStream os) {
      super(os);
    }

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
