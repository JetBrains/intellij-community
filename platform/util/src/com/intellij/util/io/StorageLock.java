// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@ApiStatus.Internal
public class StorageLock {
  private static final Logger LOG = Logger.getInstance(StorageLock.class);

  static final int MAX_PAGES_COUNT = 0xFFFF;
  private static final int MAX_LIVE_STORAGES_COUNT = 0xFFFF;

  private static final int FILE_INDEX_MASK = 0xFFFF0000;
  private static final int FILE_INDEX_SHIFT = 16;

  private static final int LOWER_LIMIT;
  private static final int UPPER_LIMIT;
  static final int BUFFER_SIZE;

  static {
    final int lower = 100;
    final int upper = SystemInfo.is64Bit ? 500 : 200;

    BUFFER_SIZE = Math.max(1, SystemProperties.getIntProperty("idea.paged.storage.page.size", 10)) * PagedFileStorage.MB;
    final long max = maxDirectMemory() - 2L * BUFFER_SIZE;
    LOWER_LIMIT = (int)Math.min(lower * PagedFileStorage.MB, max);
    UPPER_LIMIT = (int)Math.min(Math.max(LOWER_LIMIT, SystemProperties.getIntProperty("idea.max.paged.storage.cache", upper) * PagedFileStorage.MB), max);

    LOG.info("lower=" + (LOWER_LIMIT / PagedFileStorage.MB) +
             "; upper=" + (UPPER_LIMIT / PagedFileStorage.MB) +
             "; buffer=" + (BUFFER_SIZE / PagedFileStorage.MB) +
             "; max=" + (max / PagedFileStorage.MB));
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

  public StorageLock() {
    myDefaultContext = new StorageLockContext(this, true, false);

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

  int getMappingChangeCount() {
    return myMappingChangeCount;
  }

  int registerPagedFileStorage(@NotNull PagedFileStorage storage) {
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

  ByteBufferWrapper get(Integer key, boolean read, boolean readOnly) {
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
      wrapper = createValue(key, read, readOnly);

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
      iterator.next().release();
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
  private ByteBufferWrapper createValue(Integer key, boolean read, boolean readOnly) {
    final int storageIndex = key & FILE_INDEX_MASK;
    PagedFileStorage owner = getRegisteredPagedFileStorageByIndex(storageIndex);
    assert owner != null: "No storage for index " + storageIndex;
    owner.getStorageLockContext().checkThreadAccess(read);
    long off = (long)(key & MAX_PAGES_COUNT) * owner.myPageSize;
    long ownerLength = owner.length();
    if (off > ownerLength) {
      throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + ownerLength);
    }

    int min = (int)Math.min(ownerLength - off, owner.myPageSize);
    ByteBufferWrapper wrapper = readOnly
                                ? ByteBufferWrapper.readOnlyDirect(owner.getFile(), off, min)
                                : ByteBufferWrapper.readWriteDirect(owner.getFile(), off, min);
    Throwable oome = null;
    while (true) {
      try {
        // ensure it's allocated
        wrapper.getBuffer();
        if (oome != null) {
          LOG.info("Successfully recovered OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / PagedFileStorage.MB + "MB " +
                   "new size limit: " + mySizeLimit / PagedFileStorage.MB + "MB " +
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
          LOG.info("Currently allocated:" + mySize);
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
            "Cannot recover from OOME in memory mapping: -Xmx=" + Runtime.getRuntime().maxMemory() / PagedFileStorage.MB + "MB " +
            "new size limit: " + mySizeLimit / PagedFileStorage.MB + "MB " +
            "trying to allocate " + wrapper.myLength + " block", e);
        }
        ensureSize(newSize); // next try
      }
    }
  }

  @Nullable
  private Map<Integer, ByteBufferWrapper> getBuffersOrderedForOwner(int index, StorageLockContext storageLockContext, boolean read) {
    mySegmentsAccessLock.lock();
    try {
      storageLockContext.checkThreadAccess(read);
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

  void unmapBuffersForOwner(int index, StorageLockContext storageLockContext, boolean read) {
    final Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext, read);

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

  void flushBuffersForOwner(int index, StorageLockContext storageLockContext) {
    Map<Integer, ByteBufferWrapper> buffers = getBuffersOrderedForOwner(index, storageLockContext, false);

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

  void invalidateBuffer(int page) {
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

  void removeStorage(int index) {
    myIndex2Storage.remove(index);
  }
}
