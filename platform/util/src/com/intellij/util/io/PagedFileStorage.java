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
import java.nio.MappedByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author max
 */
public class PagedFileStorage implements Forceable {

  protected static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PagedFileStorage");
  private static final int MEGABYTE = 1024 * 1024;
  public final static int DEFAULT_BUFFER_SIZE;

  private final static int UPPER_LIMIT;
  private static final int LOWER_LIMIT_IN_MEGABYTES = 100;
  private final static int LOWER_LIMIT = LOWER_LIMIT_IN_MEGABYTES * MEGABYTE;
  private static final int UNKNOWN_PAGE = -1;

  static {
    String maxPagedStorageCacheProperty = System.getProperty("idea.max.paged.storage.cache");
    int defaultMaxPagedStorageCacheInMegabytes = SystemInfo.is64Bit ? 500:200;
    UPPER_LIMIT = (maxPagedStorageCacheProperty == null ? defaultMaxPagedStorageCacheInMegabytes:
                   Math.max(Integer.valueOf(maxPagedStorageCacheProperty), LOWER_LIMIT_IN_MEGABYTES))*MEGABYTE;

    String pagedStoragePageSizeProperty = System.getProperty("idea.paged.storage.page.size");
    int defaultPagedStoragePageSizeInMegabytes = 10;
    DEFAULT_BUFFER_SIZE = (pagedStoragePageSizeProperty == null ? defaultPagedStoragePageSizeInMegabytes:Math.max(1,Integer.valueOf(pagedStoragePageSizeProperty)))* MEGABYTE;
  }

  private final StorageLock myLock;
  private int myLastPage = UNKNOWN_PAGE;
  private int myLastPage2 = UNKNOWN_PAGE;
  private MappedBufferWrapper myLastBuffer;
  private MappedBufferWrapper myLastBuffer2;
  private int myLastChangeCount;
  private int myLastChangeCount2;
  private int myStorageIndex;

  private static final int MAX_PAGES_COUNT = 0xFFFF;
  private static final int MAX_LIVE_STORAGES_COUNT = 0xFFFF;

  public static class StorageLock {
    private static final int FILE_INDEX_MASK = 0xFFFF0000;
    private static final int FILE_INDEX_SHIFT = 16;
    private final boolean checkThreadAccess;

    public StorageLock() {
      this(true);
    }

    public StorageLock(boolean checkThreadAccess) {
      this.checkThreadAccess = checkThreadAccess;
    }

    private final BuffersCache myBuffersCache = new BuffersCache();
    private final ConcurrentHashMap<Integer, PagedFileStorage> myIndex2Storage = new ConcurrentHashMap<Integer, PagedFileStorage>();
    
    private int registerPagedFileStorage(PagedFileStorage storage) {
      int registered = myIndex2Storage.size();
      assert registered <= MAX_LIVE_STORAGES_COUNT;
      int value = registered << FILE_INDEX_SHIFT;
      while(myIndex2Storage.putIfAbsent(value, storage) != null) {
        ++registered;
        assert registered <= MAX_LIVE_STORAGES_COUNT;
        value = registered << FILE_INDEX_SHIFT;
      }
      return value;
    }
    
    private PagedFileStorage getRegisteredPagedFileStorageByIndex(int index) {
      return myIndex2Storage.get(index);
    }

    private class BuffersCache {
      private int changeCount;
      private final LinkedHashMap<Integer, MappedBufferWrapper> myMap;
      private long mySizeLimit;
      private long mySize;

      private BuffersCache() {
        mySizeLimit = UPPER_LIMIT;
        myMap = new LinkedHashMap<Integer, MappedBufferWrapper>(10) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Integer, MappedBufferWrapper> eldest) {
            return mySize > mySizeLimit;
          }

          @Nullable
          @Override
          public MappedBufferWrapper remove(Object key) {
            // this method can be called after removeEldestEntry
            MappedBufferWrapper wrapper = super.remove(key);
            if (wrapper != null) {
              mySize -= wrapper.myLength;
              wrapper.dispose();
            }
            return wrapper;
          }
        };
      }

      private MappedBufferWrapper get(Integer key) {
        MappedBufferWrapper wrapper = myMap.get(key);
        if (wrapper != null) {
          return wrapper;
        }

        long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;
        wrapper = createValue(key);
        mySize += wrapper.myLength;

        if (IOStatistics.DEBUG) {
          long finished = System.currentTimeMillis();
          if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
            IOStatistics.dump(
              "Mapping " + wrapper.myLength + " from " + wrapper.myPosition + " file:" + wrapper.myFile + " for " + (finished - started));
          }
        }
        myMap.put(key, wrapper);

        ensureSize(mySizeLimit);
        return wrapper;
      }

      private void ensureSize(long sizeLimit) {
        while (mySize > sizeLimit) {
          // we still have to drop something
          myMap.doRemoveEldestEntry();
        }
      }

      @NotNull
      private MappedBufferWrapper createValue(Integer key) {
        checkThreadAccess();
        PagedFileStorage owner = getRegisteredPagedFileStorageByIndex(key & FILE_INDEX_MASK);
        int off = (key & MAX_PAGES_COUNT) * owner.myPageSize;
        if (off > owner.length()) {
          throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + owner.length());
        }
        ++changeCount;
        ReadWriteMappedBufferWrapper wrapper =
          new ReadWriteMappedBufferWrapper(owner.myFile, off, Math.min((int)(owner.length() - off), owner.myPageSize));
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
                mySizeLimit -= owner.myPageSize;
              }
              long newSize = mySize - owner.myPageSize;
              if (newSize >= 0) {
                ensureSize(newSize);
                continue; // next try
              }
              else {
                throw new MappingFailedException(
                  "Cannot recover from OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MEGABYTE + "MB " +
                  "new size limit: " + mySizeLimit / MEGABYTE + "MB " +
                  "trying to allocate " + wrapper.myLength + " block", e);
              }
            }
            throw new MappingFailedException("Cannot map buffer", e);
          }
        }
      }

      private void checkThreadAccess() {
        if (checkThreadAccess && !Thread.holdsLock(StorageLock.this)) {
          throw new IllegalStateException("Must hold StorageLock lock to access PagedFileStorage");
        }
      }

      private @Nullable Map<Integer, MappedBufferWrapper> getBuffersOrderedForOwner(int index) {
        checkThreadAccess();
        Map<Integer, MappedBufferWrapper> mineBuffers = null;
        for (Map.Entry<Integer, MappedBufferWrapper> entry : myMap.entrySet()) {
          if ((entry.getKey() & FILE_INDEX_MASK) == index) {
            if (mineBuffers == null) {
              mineBuffers = new TreeMap<Integer, MappedBufferWrapper>(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                  return o1 - o2;
                }
              });
            }
            mineBuffers.put(entry.getKey(), entry.getValue());
          }
        }
        return mineBuffers;
      }

      private void unmapBuffersForOwner(int index) {
        final Map<Integer, MappedBufferWrapper> buffers = getBuffersOrderedForOwner(index);

        if (buffers != null) {
          for (Integer key : buffers.keySet()) {
            myMap.remove(key);
          }
        }
      }

      private void flushBuffersForOwner(int index) {
        Map<Integer, MappedBufferWrapper> buffers = getBuffersOrderedForOwner(index);

        if (buffers != null) {
          for(MappedBufferWrapper buffer:buffers.values()) {
            buffer.flush();
          }
        }
      }
    }
  }

  private final byte[] myTypedIOBuffer;
  private boolean isDirty = false;
  private final File myFile;
  protected long mySize = -1;
  protected final int myPageSize;
  protected final boolean myValuesAreBufferAligned;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file, StorageLock lock, int pageSize, boolean valuesAreBufferAligned) throws IOException {
    myFile = file;
    myLock = lock;
    myPageSize = Math.max(pageSize, Page.PAGE_SIZE);
    myValuesAreBufferAligned = valuesAreBufferAligned;
    myStorageIndex = lock.registerPagedFileStorage(this);
    myTypedIOBuffer = valuesAreBufferAligned ? null:new byte[8];
  }

  public PagedFileStorage(File file, StorageLock lock) throws IOException {
    this(file, lock, DEFAULT_BUFFER_SIZE, false);
  }

  public File getFile() {
    return myFile;
  }

  public void putInt(int addr, int value) {
    if (myValuesAreBufferAligned) {
      isDirty = true;
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putInt(page_offset, value);
    } else {
      Bits.putInt(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 4);
    }
  }

  public int getInt(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page).getInt(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 4);
      return Bits.getInt(myTypedIOBuffer, 0);
    }
  }

  public final void putShort(int addr, short value) {
    if (myValuesAreBufferAligned) {
      isDirty = true;
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putShort(page_offset, value);
    } else {
      Bits.putShort(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 2);
    }
  }

  int getOffsetInPage(int addr) {
    return addr % myPageSize;
  }
  
  ByteBuffer getByteBuffer(int address) {
    return getBuffer(address / myPageSize);
  }

  public final short getShort(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page).getShort(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 2);
      return Bits.getShort(myTypedIOBuffer, 0);
    }
  }

  public void putLong(int addr, long value) {
    if (myValuesAreBufferAligned) {
      isDirty = true;
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      getBuffer(page).putLong(page_offset, value);
    } else {
      Bits.putLong(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 8);
    }
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public void putByte(final int addr, final byte b) {
    put(addr, b);
  }

  public byte getByte(int addr) {
    return get(addr);
  }

  public long getLong(int addr) {
    if (myValuesAreBufferAligned) {
      int page = addr / myPageSize;
      int page_offset = addr % myPageSize;
      return getBuffer(page).getLong(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 8);
      return Bits.getLong(myTypedIOBuffer, 0);
    }
  }

  public byte get(int index) {
    int page = index / myPageSize;
    int offset = index % myPageSize;

    return getBuffer(page).get(offset);
  }

  public void put(int index, byte value) {
    isDirty = true;
    int page = index / myPageSize;
    int offset = index % myPageSize;

    getBuffer(page).put(offset, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / myPageSize;
      int page_offset = i % myPageSize;

      int page_len = Math.min(l, myPageSize - page_offset);
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
      int page = i / myPageSize;
      int page_offset = i % myPageSize;

      int page_len = Math.min(l, myPageSize - page_offset);
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
      myLock.myIndex2Storage.remove(myStorageIndex);
      myStorageIndex = -1;
    }
  }

  private void unmapAll() {
    myLock.myBuffersCache.unmapBuffersForOwner(myStorageIndex);

    myLastPage = UNKNOWN_PAGE;
    myLastPage2 = UNKNOWN_PAGE;
    myLastBuffer = null;
    myLastBuffer2 = null;
  }

  public void resize(int newSize) throws IOException {
    int oldSize = (int)myFile.length();
    if (oldSize == newSize) return;

    final long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    unmapAll();
    final long unmapAllFinished = IOStatistics.DEBUG ? System.currentTimeMillis():0;

    resizeFile(newSize);

    // it is not guaranteed that new partition will consist of null
    // after resize, so we should fill it manually
    int delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Resized "+myFile + " from " + oldSize + " to " + newSize + " for " + (finished - started) + ", unmap all:" + (finished - unmapAllFinished));
      }
    }
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
    if (myLastPage == page) {
      MappedByteBuffer buf = myLastBuffer.getIfCached();
      if (buf != null && myLastChangeCount == myLock.myBuffersCache.changeCount) return buf;
    }

    if (myLastPage2 == page) {
      MappedByteBuffer buf = myLastBuffer2.getIfCached();
      if (buf != null && myLastChangeCount2 == myLock.myBuffersCache.changeCount) return buf;
    }

    try {
      assert page <= MAX_PAGES_COUNT;

      if (myStorageIndex == -1) {
        myStorageIndex = myLock.registerPagedFileStorage(this);
      }
      MappedBufferWrapper mappedBufferWrapper = myLock.myBuffersCache.get(myStorageIndex | page);
      MappedByteBuffer buf = mappedBufferWrapper.buf();

      if (myLastPage != page) {
        myLastPage2 = myLastPage;
        myLastBuffer2 = myLastBuffer;
        myLastChangeCount2 = myLastChangeCount;
        myLastBuffer = mappedBufferWrapper;
        myLastPage = page;
      } else {
        myLastBuffer = mappedBufferWrapper;
      }

      myLastChangeCount = myLock.myBuffersCache.changeCount;

      return buf;
    }
    catch (IOException e) {
      throw new MappingFailedException("Cannot map buffer", e);
    }
  }

  public void force() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    myLock.myBuffersCache.flushBuffersForOwner(myStorageIndex);

    isDirty = false;
    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Flushed "+myFile + " for " + (finished - started));
      }
    }
  }

  public boolean isDirty() {
    return isDirty;
  }
}
