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
      appendBytes("Header Record For PersistentHashMapValuStorage".getBytes(), 0);
    }
  }

  public long appendBytes(byte[] data, long prevChunkAddress) throws IOException {
    assert !myCompactionMode;
    long result = mySize;
    final CacheValue<DataOutputStream> appender = ourAppendersCache.get(myPath);
    try {
      appender.get().writeLong(prevChunkAddress);
      appender.get().writeInt(data.length);
      appender.get().write(data);
    }
    finally {
      appender.release();
    }
    mySize += data.length + 8 + 4;

    return result;
  }

  /**
   * Reads bytes pointed by tailChunkAddress into result passed, returns new address if linked list compactification have been performed
   */
  public long readBytes(long tailChunkAddress, byte[] result) throws IOException {
    int size = result.length;
    if (size == 0) return tailChunkAddress;

    force();

    int bytesRead = 0;
    long chunk = tailChunkAddress;
    int chunkCount = 0;

    byte[] headerBits = new byte[8 + 4];
    RAReader reader = myCompactionModeReader;
    CacheValue<RAReader> readerHandle = null;
    if (reader == null) {
      readerHandle = ourReadersCache.get(myPath);
      reader = readerHandle.get();
    }

    try {
      while (chunk != 0) {
        reader.get(chunk, headerBits, 0, 12);
        final long prevChunkAddress = Bits.getLong(headerBits, 0);
        final int chunkSize = Bits.getInt(headerBits, 8);
        final int off = size - bytesRead - chunkSize;

        checkPreconditions(result, chunkSize, off);

        reader.get(chunk + 12, result, off, chunkSize);
        chunk = prevChunkAddress;
        bytesRead += chunkSize;
        chunkCount++;
      }
    }
    finally {
      if (readerHandle != null) {
        readerHandle.release();
      }
    }

    //assert bytesRead == size;
    if (bytesRead != size) {
      throw new IOException("Read from storage " + bytesRead + " bytes, but requested " + size + " bytes");
    }
    
    if (chunkCount > 1 && !myCompactionMode) {
      return appendBytes(result, 0);
    }

    return tailChunkAddress;
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

  public void switchToCompactionMode(PagedFileStorage.StorageLock lock) {
    ourReadersCache.remove(myPath);
    try {
      myCompactionModeReader = new MappedReader(myFile, lock);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myCompactionMode = true;
  }

  public static PersistentHashMapValueStorage create(final String path) throws IOException {
    return new PersistentHashMapValueStorage(path);
  }

  private interface RAReader {
    void get(long addr, byte[] dst, int off, int len) throws IOException;
    void dispose();
  }

  private static class MappedReader implements RAReader {
    private final PagedFileStorage myHolder;

    private MappedReader(File file, PagedFileStorage.StorageLock lock) throws IOException {
      myHolder = new PagedFileStorage(file, lock);
      myHolder.length();
    }

    public void get(final long addr, final byte[] dst, final int off, final int len) {
      myHolder.get((int)addr, dst, off, len);
    }

    public void dispose() {
      myHolder.close();
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
