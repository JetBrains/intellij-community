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
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class PagedFileStorage implements Forceable {

  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PagedFileStorage");
  private static final int MEGABYTE = 1024 * 1024;
  private final static int BUFFER_SIZE;

  private final static int UPPER_LIMIT;
  public static final int LOWER_LIMIT_IN_MEGABYTES = 100;
  private final static int LOWER_LIMIT = LOWER_LIMIT_IN_MEGABYTES * MEGABYTE;

  static {
    String maxPagedStorageCacheProperty = System.getProperty("idea.max.paged.storage.cache");
    int defaultMaxPagedStorageCacheInMegabytes = SystemInfo.is64Bit ? 500:200;
    UPPER_LIMIT = (maxPagedStorageCacheProperty == null ? defaultMaxPagedStorageCacheInMegabytes:
                   Math.max(Integer.valueOf(maxPagedStorageCacheProperty), LOWER_LIMIT_IN_MEGABYTES))*MEGABYTE;

    String pagedStoragePageSizeProperty = System.getProperty("idea.paged.storage.page.size");
    int defaultPagedStoragePageSizeInMegabytes = 10;
    BUFFER_SIZE = (maxPagedStorageCacheProperty == null ? defaultPagedStoragePageSizeInMegabytes:Math.max(1,Integer.valueOf(pagedStoragePageSizeProperty)))* MEGABYTE;
  }

  private final StorageLock myLock;

  public static class StorageLock {
    private final boolean checkThreadAccess;

    public StorageLock() {
      this(true);
    }

    public StorageLock(boolean checkThreadAccess) {
      this.checkThreadAccess = checkThreadAccess;
    }

    final BuffersCache myBuffersCache = new BuffersCache();

    private class BuffersCache extends MyCache {

      public BuffersCache() {
        super(UPPER_LIMIT);
      }

      @NotNull
      public MappedBufferWrapper createValue(PageKey key) {
        if (checkThreadAccess && !Thread.holdsLock(StorageLock.this)) {
          throw new IllegalStateException("Must hold StorageLock lock to access PagedFileStorage");
        }

        int off = key.page * BUFFER_SIZE;
        if (off > key.owner.length()) {
          throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + key.owner.length());
        }
        ReadWriteMappedBufferWrapper wrapper =
          new ReadWriteMappedBufferWrapper(key.owner.myFile, off, Math.min((int)(key.owner.length() - off), BUFFER_SIZE));
        IOException oome = null;
        while (true) {
          try {
            // ensure it's allocated
            wrapper.buf();
            if (oome != null) {
              LOG.info("Successfully recovered OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MEGABYTE + "MB " +
                       "new size limit: " + mySizeLimit / MEGABYTE + "MB " +
                       "trying to allocate " + wrapper.myLength + " block");
            }
            return wrapper;
          }
          catch (IOException e) {
            if (e.getCause() instanceof OutOfMemoryError) {
              oome = e;
              if (mySizeLimit > LOWER_LIMIT) {
                mySizeLimit -= BUFFER_SIZE;
              }
              long newSize = getSize() - BUFFER_SIZE;
              if (newSize >= 0) {
                ensureSize(newSize);
                continue; // next try
              }
              else {
                throw new MappingFailedException("Cannot recover from OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MEGABYTE + "MB " +
                        "new size limit: " + mySizeLimit / MEGABYTE + "MB " +
                        "trying to allocate " + wrapper.myLength + " block", e);
              }
            }
            throw new MappingFailedException("Cannot map buffer", e);
          }
        }
      }

      public void onDropFromCache(PageKey key, MappedBufferWrapper buf) {
        buf.dispose();
      }
    }
  }

  private static class PageKey {
    private final PagedFileStorage owner;
    private final int page;

    public PageKey(PagedFileStorage owner, int page) {
      this.owner = owner;
      this.page = page;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PageKey)) return false;

      PageKey pageKey = (PageKey)o;

      if (!owner.equals(pageKey.owner)) return false;
      if (page != pageKey.page) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * owner.hashCode() + page;
    }
  }


  private final byte[] myTypedIOBuffer = new byte[8];
  private boolean isDirty = false;
  private final File myFile;
  protected long mySize = -1;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file, StorageLock lock) throws IOException {
    myFile = file;
    myLock = lock;
  }

  public File getFile() {
    return myFile;
  }

  public void putInt(int addr, int value) {
    Bits.putInt(myTypedIOBuffer, 0, value);
    put(addr, myTypedIOBuffer, 0, 4);
  }

  public int getInt(int addr) {
    get(addr, myTypedIOBuffer, 0, 4);
    return Bits.getInt(myTypedIOBuffer, 0);
  }

  public void putLong(int addr, long value) {
    Bits.putLong(myTypedIOBuffer, 0, value);
    put(addr, myTypedIOBuffer, 0, 8);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void putByte(final int addr, final byte b) {
    myTypedIOBuffer[0] = b;
    put(addr, myTypedIOBuffer, 0, 1);
  }

  public byte getByte(int addr) {
    get(addr, myTypedIOBuffer, 0, 1);
    return myTypedIOBuffer[0];
  }

  public long getLong(int addr) {
    get(addr, myTypedIOBuffer, 0, 8);
    return Bits.getLong(myTypedIOBuffer, 0);
  }

  public byte get(int index) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    return getBuffer(page).get(offset);
  }

  public void put(int index, byte value) {
    isDirty = true;
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    getBuffer(page).put(offset, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / BUFFER_SIZE;
      int page_offset = i % BUFFER_SIZE;

      int page_len = Math.min(l, BUFFER_SIZE - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      try {
        buffer.position(page_offset);
      }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("can't position buffer to offset " + page_offset + ", " +
                                           "buffer.limit=" + buffer.limit() + ", " +
                                           "page=" + page + ", " +
                                           "file=" + myFile.getName() + ", "+
                                           "file.length=" + mySize);
      }
      buffer.get(dst, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(int index, byte[] src, int offset, int length) {
    isDirty = true;
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / BUFFER_SIZE;
      int page_offset = i % BUFFER_SIZE;

      int page_len = Math.min(l, BUFFER_SIZE - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      try {
        buffer.position(page_offset);
      }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("can't position buffer to offset " + page_offset);
      }
      buffer.put(src, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void close() {
    try {
      force();
    }
    finally {
      unmapAll();
    }
  }

  private void unmapAll() {
    for (Map.Entry<PageKey, MappedBufferWrapper> entry : myLock.myBuffersCache.entrySet()) {
      if (entry.getKey().owner == this) {
        myLock.myBuffersCache.remove(entry.getKey());
      }
    }
  }

  public void resize(int newSize) throws IOException {
    int oldSize = (int)myFile.length();
    if (oldSize == newSize) return;

    unmapAll();
    resizeFile(newSize);

    // it is not guaranteed that new partition will consist of null
    // after resize, so we should fill it manually
    int delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);
  }

  private void resizeFile(int newSize) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(myFile, RW);
    try {
      raf.setLength(newSize);
    }
    finally {
      raf.close();
    }
    mySize = newSize;
  }

  private final static int MAX_FILLER_SIZE = 8192;
  private void fillWithZeros(int from, int length) {
    byte[] buff = new byte[MAX_FILLER_SIZE];
    Arrays.fill(buff, (byte)0);

    while (length > 0) {
      final int filled = Math.min(length, MAX_FILLER_SIZE);
      put(from, buff, 0, filled);
      length -= filled;
      from += filled;
    }
  }


  public final long length() {
    if (mySize == -1) {
      mySize = myFile.length();
    }
    return mySize;
  }

  private ByteBuffer getBuffer(int page) {
    try {
      return myLock.myBuffersCache.get(new PageKey(this, page)).buf();
    }
    catch (IOException e) {
      throw new MappingFailedException("Cannot map buffer", e);
    }
  }

  public void force() {
    for (Map.Entry<PageKey,MappedBufferWrapper> entry : myLock.myBuffersCache.entrySet()) {
      if (entry.getKey().owner == this) {
        entry.getValue().flush();
      }
    }
    isDirty = false;
  }

  public boolean isDirty() {
    return isDirty;
  }

  private static abstract class MyCache {

    private final LinkedHashMap<PageKey, MappedBufferWrapper> myMap;
    protected long mySizeLimit;
    private long mySize;

    protected MyCache(long sizeLimit) {
      mySizeLimit = sizeLimit;
      myMap = new LinkedHashMap<PageKey, MappedBufferWrapper>(10) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PageKey, MappedBufferWrapper> eldest) {
          return mySize > mySizeLimit;
        }

        @Nullable
        @Override
        public MappedBufferWrapper remove(Object key) {
          // this method can be called after removeEldestEntry
          MappedBufferWrapper wrapper = super.remove(key);
          if (wrapper != null) {
            mySize -= wrapper.myLength;
            onDropFromCache((PageKey)key, wrapper);
          }
          return wrapper;
        }
      };
    }

    public MappedBufferWrapper get(PageKey key) {
      MappedBufferWrapper wrapper = myMap.get(key);
      if (wrapper != null) {
        return wrapper;
      }
      wrapper = createValue(key);
      mySize += wrapper.myLength;
      myMap.put(key, wrapper);
      ensureSize(mySizeLimit);
      return wrapper;
    }

    protected void ensureSize(long sizeLimit) {
      while (mySize > sizeLimit) {
        // we still have to drop something
        myMap.doRemoveEldestEntry();
      }
    }

    public long getSize() {
      return mySize;
    }

    public Set<Map.Entry<PageKey, MappedBufferWrapper>> entrySet() {
      return myMap.entrySet();
    }

    public void remove(PageKey key) {
      myMap.remove(key);
    }

    protected abstract MappedBufferWrapper createValue(PageKey key);

    protected abstract void onDropFromCache(PageKey key, MappedBufferWrapper wrapper);
  }
}
