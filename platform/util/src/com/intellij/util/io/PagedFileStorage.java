// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class PagedFileStorage implements Forceable {
  private static final Logger LOG = Logger.getInstance(PagedFileStorage.class);

  public static final int MB = 1024 * 1024;
  public static final int BUFFER_SIZE;

  private static final int LOWER_LIMIT;
  private static final int UPPER_LIMIT;

  static {
    final int lower = 100;
    final int upper = SystemInfo.is64Bit ? 500 : 200;

    BUFFER_SIZE = Math.max(1, SystemProperties.getIntProperty("idea.paged.storage.page.size", 10)) * MB;
    final long max = maxDirectMemory() - 2L * BUFFER_SIZE;
    LOWER_LIMIT = (int)Math.min(lower * MB, max);
    UPPER_LIMIT = (int)Math.min(Math.max(LOWER_LIMIT, SystemProperties.getIntProperty("idea.max.paged.storage.cache", upper) * MB), max);

    LOG.info("lower=" + (LOWER_LIMIT / MB) + "; upper=" + (UPPER_LIMIT / MB) + "; buffer=" + (BUFFER_SIZE / MB) + "; max=" + (max / MB));
  }

  private static long maxDirectMemory() {
    try {
      Class<?> aClass = Class.forName("sun.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) { }

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("maxMemory");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) { }

    return Runtime.getRuntime().maxMemory();
  }

  private static final int UNKNOWN_PAGE = -1;
  private static final int MAX_PAGES_COUNT = 0xFFFF;
  private static final int MAX_LIVE_STORAGES_COUNT = 0xFFFF;
  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();
  private static final String RW = "rw";

  // It is important to have ourLock after previous static constants as it depends on them
  private static final StorageLock ourLock = new StorageLock();

  private final StorageLockContext myStorageLockContext;
  private final boolean myNativeBytesOrder;
  private int myLastPage = UNKNOWN_PAGE;
  private int myLastPage2 = UNKNOWN_PAGE;
  private int myLastPage3 = UNKNOWN_PAGE;
  private ByteBufferWrapper myLastBuffer;
  private ByteBufferWrapper myLastBuffer2;
  private ByteBufferWrapper myLastBuffer3;
  private int myLastChangeCount;
  private int myLastChangeCount2;
  private int myLastChangeCount3;
  private int myStorageIndex;
  private final Object myLastAccessedBufferCacheLock = new Object();

  private final byte[] myTypedIOBuffer;
  private volatile boolean isDirty;
  private final Path myFile;
  protected volatile long mySize = -1;
  protected final int myPageSize;
  protected final boolean myValuesAreBufferAligned;

  public PagedFileStorage(Path file,
                          @Nullable StorageLockContext storageLockContext,
                          int pageSize,
                          boolean valuesAreBufferAligned,
                          boolean nativeBytesOrder) {
    myFile = file;
    myStorageLockContext = storageLockContext != null ? storageLockContext : ourLock.myDefaultContext;
    myPageSize = Math.max(pageSize > 0 ? pageSize : BUFFER_SIZE, Page.PAGE_SIZE);
    myValuesAreBufferAligned = valuesAreBufferAligned;
    myStorageIndex = myStorageLockContext.myStorageLock.registerPagedFileStorage(this);
    myTypedIOBuffer = valuesAreBufferAligned ? null:new byte[8];
    myNativeBytesOrder = nativeBytesOrder;
  }

  public int getPageSize() {
    return myPageSize;
  }

  public void lock() {
    myStorageLockContext.lock();
  }

  public void unlock() {
    myStorageLockContext.unlock();
  }

  public StorageLockContext getStorageLockContext() {
    return myStorageLockContext;
  }

  public Path getFile() {
    return myFile;
  }

  public void putInt(long addr, int value) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putInt(page_offset, value);
    } else {
      Bits.putInt(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 4);
    }
  }

  public int getInt(long addr) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int) (addr % myPageSize);
      return getReadOnlyBuffer(page).getInt(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 4);
      return Bits.getInt(myTypedIOBuffer, 0);
    }
  }

  public final void putShort(long addr, short value) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putShort(page_offset, value);
    } else {
      Bits.putShort(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 2);
    }
  }

  public int getOffsetInPage(long addr) {
    return (int)(addr % myPageSize);
  }

  public ByteBufferWrapper getByteBuffer(long address, boolean modify) {
    long page = address / myPageSize;
    assert page >= 0 && page <= MAX_PAGES_COUNT:address + " in " + myFile;
    return getBufferWrapper(page, modify);
  }

  public final short getShort(long addr) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      return getReadOnlyBuffer(page).getShort(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 2);
      return Bits.getShort(myTypedIOBuffer, 0);
    }
  }

  public void putLong(long addr, long value) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      getBuffer(page).putLong(page_offset, value);
    } else {
      Bits.putLong(myTypedIOBuffer, 0, value);
      put(addr, myTypedIOBuffer, 0, 8);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public void putByte(final long addr, final byte b) {
    put(addr, b);
  }

  public byte getByte(long addr) {
    return get(addr);
  }

  public long getLong(long addr) {
    if (myValuesAreBufferAligned) {
      long page = addr / myPageSize;
      int page_offset = (int)(addr % myPageSize);
      return getReadOnlyBuffer(page).getLong(page_offset);
    } else {
      get(addr, myTypedIOBuffer, 0, 8);
      return Bits.getLong(myTypedIOBuffer, 0);
    }
  }

  public byte get(long index) {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    return getReadOnlyBuffer(page).get(offset);
  }

  public void put(long index, byte value) {
    long page = index / myPageSize;
    int offset = (int)(index % myPageSize);

    getBuffer(page).put(offset, value);
  }

  public void get(long index, byte[] dst, int offset, int length) {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int) (i % myPageSize);

      int page_len = Math.min(l, myPageSize - page_offset);
      final ByteBuffer buffer = getReadOnlyBuffer(page);
      try {
        buffer.position(page_offset);
      }
      catch (IllegalArgumentException iae) {
        throw new IllegalArgumentException("can't position buffer to offset " + page_offset + ", " +
                                           "buffer.limit=" + buffer.limit() + ", " +
                                           "page=" + page + ", " +
                                           "file=" + myFile.getFileName() + ", "+
                                           "file.length=" + length());
      }
      buffer.get(dst, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(long index, byte[] src, int offset, int length) {
    long i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      long page = i / myPageSize;
      int page_offset = (int) (i % myPageSize);

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
      myStorageLockContext.myStorageLock.myIndex2Storage.remove(myStorageIndex);
      myStorageIndex = -1;
    }
  }

  private void unmapAll() {
    myStorageLockContext.myStorageLock.unmapBuffersForOwner(myStorageIndex, myStorageLockContext);

    synchronized (myLastAccessedBufferCacheLock) {
      myLastPage = UNKNOWN_PAGE;
      myLastPage2 = UNKNOWN_PAGE;
      myLastPage3 = UNKNOWN_PAGE;
      myLastBuffer = null;
      myLastBuffer2 = null;
      myLastBuffer3 = null;
    }
  }

  public void resize(long newSize) throws IOException {
    long oldSize = Files.exists(myFile) ? Files.size(myFile) : 0;
    if (oldSize == newSize && oldSize == length()) return;

    final long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    myStorageLockContext.myStorageLock.invalidateBuffer(myStorageIndex | (int)(oldSize / myPageSize)); // TODO long page
    final long unmapAllFinished = IOStatistics.DEBUG ? System.currentTimeMillis():0;

    resizeFile(newSize);

    // it is not guaranteed that new partition will consist of null
    // after resize, so we should fill it manually
    long delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Resized "+myFile + " from " + oldSize + " to " + newSize + " for " + (finished - started) + ", unmap all:" + (finished - unmapAllFinished));
      }
    }
  }

  private void resizeFile(long newSize) throws IOException {
    mySize = -1;
    try (RandomAccessFile raf = new RandomAccessFile(myFile.toFile(), RW)) {
      raf.setLength(newSize);
    }
    mySize = newSize;
  }

  private static final int MAX_FILLER_SIZE = 8192;

  private void fillWithZeros(long from, long length) {
    byte[] buff = new byte[MAX_FILLER_SIZE];
    Arrays.fill(buff, (byte)0);

    while (length > 0) {
      final int filled = Math.min((int)length, MAX_FILLER_SIZE);
      put(from, buff, 0, filled);
      length -= filled;
      from += filled;
    }
  }

  public final long length() {
    long size = mySize;
    if (size == -1) {
      if (Files.exists(myFile)) {
        try {
          mySize = size = Files.size(myFile);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      } else {
        mySize = size = 0;
      }
    }
    return size;
  }

  private ByteBuffer getBuffer(long page) {
    return getBufferWrapper(page, true).getCachedBuffer();
  }

  private ByteBuffer getReadOnlyBuffer(long page) {
    return getBufferWrapper(page, false).getCachedBuffer();
  }

  private ByteBufferWrapper getBufferWrapper(long page, boolean modify) {
    synchronized (myLastAccessedBufferCacheLock) {
      if (myLastPage == page) {
        ByteBuffer buf = myLastBuffer.getCachedBuffer();
        if (buf != null && myLastChangeCount == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer);
          return myLastBuffer;
        }
      } else if (myLastPage2 == page) {
        ByteBuffer buf = myLastBuffer2.getCachedBuffer();
        if (buf != null && myLastChangeCount2 == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer2);
          return myLastBuffer2;
        }
      } else if (myLastPage3 == page) {
        ByteBuffer buf = myLastBuffer3.getCachedBuffer();
        if (buf != null && myLastChangeCount3 == myStorageLockContext.myStorageLock.myMappingChangeCount) {
          if (modify) markDirty(myLastBuffer3);
          return myLastBuffer3;
        }
      }
    }

    try {
      assert page >= 0 && page <= MAX_PAGES_COUNT:page;

      if (myStorageIndex == -1) {
        myStorageIndex = myStorageLockContext.myStorageLock.registerPagedFileStorage(this);
      }
      ByteBufferWrapper byteBufferWrapper = myStorageLockContext.myStorageLock.get(myStorageIndex | (int)page); // TODO: long page
      if (modify) markDirty(byteBufferWrapper);
      ByteBuffer buf = byteBufferWrapper.getBuffer();
      if (myNativeBytesOrder && buf.order() != ourNativeByteOrder) {
        buf.order(ourNativeByteOrder);
      }

      synchronized (myLastAccessedBufferCacheLock) {
        if (myLastPage != page) {
          myLastPage3 = myLastPage2;
          myLastBuffer3 = myLastBuffer2;
          myLastChangeCount3 = myLastChangeCount2;

          myLastPage2 = myLastPage;
          myLastBuffer2 = myLastBuffer;
          myLastChangeCount2 = myLastChangeCount;

          myLastBuffer = byteBufferWrapper;
          myLastPage = (int)page; // TODO long page
        } else {
          myLastBuffer = byteBufferWrapper;
        }

        myLastChangeCount = myStorageLockContext.myStorageLock.myMappingChangeCount;
      }

      return byteBufferWrapper;
    }
    catch (IOException e) {
      throw new MappingFailedException("Cannot map buffer", e);
    }
  }

  private void markDirty(ByteBufferWrapper buffer) {
    if (!isDirty) isDirty = true;
    buffer.markDirty();
  }

  @Override
  public void force() {
    long started = IOStatistics.DEBUG ? System.currentTimeMillis():0;
    if (isDirty) {
      myStorageLockContext.myStorageLock.flushBuffersForOwner(myStorageIndex, myStorageLockContext);
      isDirty = false;
    }

    if (IOStatistics.DEBUG) {
      long finished = System.currentTimeMillis();
      if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
        IOStatistics.dump("Flushed "+myFile + " for " + (finished - started));
      }
    }
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }

  @ApiStatus.Internal
  public static class StorageLock {
    private static final int FILE_INDEX_MASK = 0xFFFF0000;
    private static final int FILE_INDEX_SHIFT = 16;
    public final StorageLockContext myDefaultContext;
    private final ConcurrentIntObjectMap<PagedFileStorage> myIndex2Storage = ContainerUtil.createConcurrentIntObjectMap();

    private final LinkedHashMap<Integer, ByteBufferWrapper> mySegments;
    private final ReentrantLock mySegmentsAccessLock = new ReentrantLock(); // protects map operations of mySegments, needed for LRU order, mySize and myMappingChangeCount
    // todo avoid locking for access

    private final ReentrantLock mySegmentsAllocationLock = new ReentrantLock();
    private final ConcurrentLinkedQueue<ByteBufferWrapper> mySegmentsToRemove = new ConcurrentLinkedQueue<>();
    private volatile long mySize;
    private volatile long mySizeLimit;
    private volatile int myMappingChangeCount;

    private StorageLock() {
      myDefaultContext = new StorageLockContext(this, true);

      mySizeLimit = UPPER_LIMIT;
      mySegments = new LinkedHashMap<Integer, ByteBufferWrapper>(10, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ByteBufferWrapper> eldest) {
          return mySize > mySizeLimit;
        }

        @Nullable
        @Override
        public ByteBufferWrapper remove(Object key) {
          // this method can be called after removeEldestEntry
          ByteBufferWrapper wrapper = super.remove(key);
          if (wrapper != null) {
            ++myMappingChangeCount;
            mySegmentsToRemove.offer(wrapper);
            mySize -= wrapper.myLength;
          }
          return wrapper;
        }
      };
    }

    private int registerPagedFileStorage(@NotNull PagedFileStorage storage) {
      int registered = myIndex2Storage.size();
      assert registered <= MAX_LIVE_STORAGES_COUNT;
      int value = registered << FILE_INDEX_SHIFT;
      while(myIndex2Storage.cacheOrGet(value, storage) != storage) {
        ++registered;
        assert registered <= MAX_LIVE_STORAGES_COUNT;
        value = registered << FILE_INDEX_SHIFT;
      }
      return value;
    }

    private PagedFileStorage getRegisteredPagedFileStorageByIndex(int index) {
      return myIndex2Storage.get(index);
    }

    private ByteBufferWrapper get(Integer key) throws IOException {
      ByteBufferWrapper wrapper;
      try {         // fast path
        mySegmentsAccessLock.lock();
        wrapper = mySegments.get(key);
        if (wrapper != null) return wrapper;
      }
      finally {
        mySegmentsAccessLock.unlock();
      }

      mySegmentsAllocationLock.lock();
      try {
        // check if anybody cared about our segment
        mySegmentsAccessLock.lock();
        try {
          wrapper = mySegments.get(key);
          if (wrapper != null) return wrapper;
        } finally {
          mySegmentsAccessLock.unlock();
        }

        long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;
        wrapper = createValue(key);

        if (IOStatistics.DEBUG) {
          long finished = System.currentTimeMillis();
          if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
            IOStatistics.dump(
              "Mapping " + wrapper.myLength + " from " + wrapper.myPosition + " file:" + wrapper.myFile + " for " + (finished - started));
          }
        }

        mySegmentsAccessLock.lock();
        try {
          mySegments.put(key, wrapper);
          mySize += wrapper.myLength;
        }
        finally {
          mySegmentsAccessLock.unlock();
        }

        ensureSize(mySizeLimit);

        return wrapper;
      }
      finally {
        mySegmentsAllocationLock.unlock();
      }
    }

    private void disposeRemovedSegments() {
      if (mySegmentsToRemove.isEmpty()) return;

      assert mySegmentsAllocationLock.isHeldByCurrentThread();
      Iterator<ByteBufferWrapper> iterator = mySegmentsToRemove.iterator();
      while(iterator.hasNext()) {
        iterator.next().dispose();
        iterator.remove();
      }
    }

    private void ensureSize(long sizeLimit) {
      assert mySegmentsAllocationLock.isHeldByCurrentThread();

      try {
        mySegmentsAccessLock.lock();
        while (mySize > sizeLimit) {
          // we still have to drop something
          mySegments.doRemoveEldestEntry();
        }
      } finally {
        mySegmentsAccessLock.unlock();
      }

      disposeRemovedSegments();
    }

    @NotNull
    private ByteBufferWrapper createValue(Integer key) {
      final int storageIndex = key & FILE_INDEX_MASK;
      PagedFileStorage owner = getRegisteredPagedFileStorageByIndex(storageIndex);
      assert owner != null: "No storage for index " + storageIndex;
      checkThreadAccess(owner.myStorageLockContext);
      long off = (long)(key & MAX_PAGES_COUNT) * owner.myPageSize;
      long ownerLength = owner.length();
      if (off > ownerLength) {
        throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + ownerLength);
      }

      int min = (int)Math.min(ownerLength - off, owner.myPageSize);
      ByteBufferWrapper wrapper = ByteBufferWrapper.readWriteDirect(owner.myFile, off, min);
      Throwable oome = null;
      while (true) {
        try {
          // ensure it's allocated
          wrapper.getBuffer();
          if (oome != null) {
            LOG.info("Successfully recovered OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MB + "MB " +
                     "new size limit: " + mySizeLimit / MB + "MB " +
                     "trying to allocate " + wrapper.myLength + " block");
          }
          return wrapper;
        }
        catch (IOException e) {
          throw new MappingFailedException("Cannot map buffer", e);
        }
        catch (OutOfMemoryError e) {
          oome = e;
          if (mySizeLimit > LOWER_LIMIT) {
            mySizeLimit -= owner.myPageSize;
          }
          long newSize = mySize - owner.myPageSize;
          if (newSize < 0) {
            LOG.info("Currently allocated:"+mySize);
            LOG.info("Mapping failed due to OOME. Current buffers: " + mySegments);
            LOG.info(oome);
            try {
              Class<?> aClass = Class.forName("java.nio.Bits");
              Field reservedMemory = aClass.getDeclaredField("reservedMemory");
              reservedMemory.setAccessible(true);
              Field maxMemory = aClass.getDeclaredField("maxMemory");
              maxMemory.setAccessible(true);
              Object max, reserved;
              //noinspection SynchronizationOnLocalVariableOrMethodParameter
              synchronized (aClass) {
                max = maxMemory.get(null);
                reserved = reservedMemory.get(null);
              }
              LOG.info("Max memory:" + max + ", reserved memory:" + reserved);
            }
            catch (Throwable ignored) { }
            throw new MappingFailedException(
              "Cannot recover from OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / MB + "MB " +
              "new size limit: " + mySizeLimit / MB + "MB " +
              "trying to allocate " + wrapper.myLength + " block", e);
          }
          ensureSize(newSize); // next try
        }
      }
    }

    private static void checkThreadAccess(StorageLockContext storageLockContext) {
      if (storageLockContext.myCheckThreadAccess && !storageLockContext.myLock.isHeldByCurrentThread()) {
        throw new IllegalStateException("Must hold StorageLock lock to access PagedFileStorage");
      }
    }

    @Nullable
    private Map<Integer, ByteBufferWrapper> getBuffersOrderedForOwner(int index, StorageLockContext storageLockContext) {
      mySegmentsAccessLock.lock();
      try {
        checkThreadAccess(storageLockContext);
        Map<Integer, ByteBufferWrapper> mineBuffers = null;
        for (Map.Entry<Integer, ByteBufferWrapper> entry : mySegments.entrySet()) {
          if ((entry.getKey() & FILE_INDEX_MASK) == index) {
            if (mineBuffers == null) {
              mineBuffers = new TreeMap<>(Comparator.comparingInt(o -> o));
            }
            mineBuffers.put(entry.getKey(), entry.getValue());
          }
        }
        return mineBuffers;
      }
      finally {
        mySegmentsAccessLock.unlock();
      }
    }

    private void unmapBuffersForOwner(int index, StorageLockContext storageLockContext) {
      final Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext);

      if (buffers != null) {
        mySegmentsAccessLock.lock();
        try {
          for (Integer key : buffers.keySet()) {
            mySegments.remove(key);
          }
        }
        finally {
          mySegmentsAccessLock.unlock();
        }

        mySegmentsAllocationLock.lock();
        try {
          disposeRemovedSegments();
        } finally {
          mySegmentsAllocationLock.unlock();
        }
      }
    }

    private void flushBuffersForOwner(int index, StorageLockContext storageLockContext) {
      Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext);

      if (buffers != null) {
        mySegmentsAllocationLock.lock();
        try {
          ReadWriteDirectBufferWrapper.FileContext fileContext = null;
          for (ByteBufferWrapper buffer : buffers.values()) {
            if (buffer instanceof ReadWriteDirectBufferWrapper) {
              fileContext = ((ReadWriteDirectBufferWrapper)buffer).flushWithContext(fileContext);
            }
            else {
              buffer.flush();
            }
          }
          if (fileContext != null) {
            fileContext.close();
          }
        }
        finally {
          mySegmentsAllocationLock.unlock();
        }
      }
    }

    public void invalidateBuffer(int page) {
      mySegmentsAccessLock.lock();
      try {
        mySegments.remove(page);
      } finally {
        mySegmentsAccessLock.unlock();
      }
      mySegmentsAllocationLock.lock();
      try {
        disposeRemovedSegments();
      }
      finally {
        mySegmentsAllocationLock.unlock();
      }
    }
  }

  public static class StorageLockContext {
    private final boolean myCheckThreadAccess;
    private final ReentrantLock myLock;
    private final StorageLock myStorageLock;

    private StorageLockContext(StorageLock lock, boolean checkAccess) {
      myLock = new ReentrantLock();
      myStorageLock = lock;
      myCheckThreadAccess = checkAccess;
    }

    public StorageLockContext(boolean checkAccess) {
      this(ourLock, checkAccess);
    }

    public void lock() {
      myLock.lock();
    }
    public void unlock() {
      myLock.unlock();
    }
  }
}
