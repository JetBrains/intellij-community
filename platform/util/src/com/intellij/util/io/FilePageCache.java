// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.system.CpuArch;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@ApiStatus.Internal
public final class FilePageCache {
  private static final Logger LOG = Logger.getInstance(FilePageCache.class);

  static final int MAX_PAGES_COUNT = 0xFFFF;
  private static final int MAX_LIVE_STORAGES_COUNT = 0xFFFF;

  private static final int FILE_INDEX_MASK = 0xFFFF0000;
  private static final int FILE_INDEX_SHIFT = 16;

  private static final int LOWER_LIMIT;
  private static final int UPPER_LIMIT;
  static final int BUFFER_SIZE;

  static {
    final int lower = 100;
    final int upper = CpuArch.is32Bit() ? 200 : 500;

    BUFFER_SIZE = Math.max(1, SystemProperties.getIntProperty("idea.paged.storage.page.size", 10)) * PagedFileStorage.MB;
    final long max = maxDirectMemory() - 2L * BUFFER_SIZE;
    LOWER_LIMIT = (int)Math.min(lower * PagedFileStorage.MB, max);
    UPPER_LIMIT = (int)Math.min(Math.max(LOWER_LIMIT, SystemProperties.getIntProperty("idea.max.paged.storage.cache", upper) * PagedFileStorage.MB), max);

    LOG.info("lower=" + (LOWER_LIMIT / PagedFileStorage.MB) +
             "; upper=" + (UPPER_LIMIT / PagedFileStorage.MB) +
             "; buffer=" + (BUFFER_SIZE / PagedFileStorage.MB) +
             "; max=" + (max / PagedFileStorage.MB));
  }

  void assertUnderSegmentAllocationLock() {
    assert mySegmentsAllocationLock.isHeldByCurrentThread();
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

  private final Int2ObjectMap<PagedFileStorage> myIndex2Storage = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

  private final LinkedHashMap<Integer, DirectBufferWrapper> mySegments;

  private final ReentrantLock mySegmentsAccessLock = new ReentrantLock(); // protects map operations of mySegments, needed for LRU order, mySize and myMappingChangeCount
  // todo avoid locking for access
  private final ReentrantLock mySegmentsAllocationLock = new ReentrantLock();
  private final ConcurrentLinkedQueue<DirectBufferWrapper> mySegmentsToRemove = new ConcurrentLinkedQueue<>();

  private final long mySizeLimit;
  private long mySize;
  private volatile int myMappingChangeCount;

  public FilePageCache() {
    mySizeLimit = UPPER_LIMIT;
    mySegments = new LinkedHashMap<Integer, DirectBufferWrapper>(10, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Integer, DirectBufferWrapper> eldest) {
        assert mySegmentsAccessLock.isHeldByCurrentThread();
        return mySize > mySizeLimit;
      }

      @Nullable
      @Override
      public DirectBufferWrapper remove(Object key) {
        assert mySegmentsAccessLock.isHeldByCurrentThread();
        // this method can be called after removeEldestEntry
        DirectBufferWrapper wrapper = super.remove(key);
        if (wrapper != null) {
          //noinspection NonAtomicOperationOnVolatileField
          ++myMappingChangeCount;
          mySegmentsToRemove.offer(wrapper);
          mySize -= wrapper.getLength();
        }
        return wrapper;
      }
    };
  }

  int getMappingChangeCount() {
    return myMappingChangeCount;
  }

  int registerPagedFileStorage(@NotNull PagedFileStorage storage) {
    synchronized (myIndex2Storage) {
      int registered = myIndex2Storage.size();
      assert registered <= MAX_LIVE_STORAGES_COUNT;
      int value = registered << FILE_INDEX_SHIFT;
      while(myIndex2Storage.get(value) != null) {
        ++registered;
        assert registered <= MAX_LIVE_STORAGES_COUNT;
        value = registered << FILE_INDEX_SHIFT;
      }
      myIndex2Storage.put(value, storage);
      return value;
    }
  }

  @NotNull("Seems accessed storage has been closed")
  private PagedFileStorage getRegisteredPagedFileStorageByIndex(int key) {
    final int storageIndex = key & FILE_INDEX_MASK;
    return myIndex2Storage.get(storageIndex);
  }

  DirectBufferWrapper get(Integer key, boolean read, boolean readOnly) throws IOException {
    DirectBufferWrapper wrapper;
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
      PagedFileStorage fileStorage = getRegisteredPagedFileStorageByIndex(key);

      disposeRemovedSegments();

      wrapper = createValue(key, read, readOnly, fileStorage);

      if (IOStatistics.DEBUG) {
        long finished = System.currentTimeMillis();
        if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
          IOStatistics.dump(
            "Mapping " + wrapper + " for " + (finished - started));
        }
      }

      mySegmentsAccessLock.lock();
      try {
        mySegments.put(key, wrapper);
        mySize += wrapper.getLength();
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
    assertUnderSegmentAllocationLock();

    if (mySegmentsToRemove.isEmpty()) return;
    Iterator<DirectBufferWrapper> iterator = mySegmentsToRemove.iterator();
    while (iterator.hasNext()) {
      try {
        iterator.next().release();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      iterator.remove();
    }
  }

  private void ensureSize(long sizeLimit) {
    mySegmentsAllocationLock.isHeldByCurrentThread();

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
  private static DirectBufferWrapper createValue(Integer key, boolean read, boolean readOnly, PagedFileStorage owner) throws IOException {
    StorageLockContext context = owner.getStorageLockContext();
    if (read) {
      context.checkReadAccess();
    }
    else {
      context.checkWriteAccess();
    }
    long off = (long)(key & MAX_PAGES_COUNT) * owner.myPageSize;
    long ownerLength = owner.length();
    if (off > ownerLength) {
      throw new IndexOutOfBoundsException("off=" + off + " key.owner.length()=" + ownerLength);
    }

    int min = (int)Math.min(ownerLength - off, owner.myPageSize);
    return readOnly
           ? DirectBufferWrapper.readOnlyDirect(owner, off, min)
           : DirectBufferWrapper.readWriteDirect(owner, off, min);
  }

  @NotNull
  private Map<Integer, DirectBufferWrapper> getBuffersOrderedForOwner(@NotNull StorageLockContext storageLockContext) {
    mySegmentsAccessLock.lock();
    try {
      storageLockContext.checkReadAccess();
      Map<Integer, DirectBufferWrapper> mineBuffers = new TreeMap<>(Comparator.comparingInt(o -> o));
      for (Map.Entry<Integer, DirectBufferWrapper> entry : mySegments.entrySet()) {
        if (entry.getValue().belongs(storageLockContext)) {
          mineBuffers.put(entry.getKey(), entry.getValue());
        }
      }
      return mineBuffers;
    }
    finally {
      mySegmentsAccessLock.unlock();
    }
  }

  void unmapBuffersForOwner(@NotNull StorageLockContext storageLockContext) {
    final Map<Integer, DirectBufferWrapper> buffers = getBuffersOrderedForOwner(storageLockContext);

    if (!buffers.isEmpty()) {
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

  void flushBuffersForOwner(StorageLockContext storageLockContext) throws IOException {
    storageLockContext.checkReadAccess();
    Map<Integer, DirectBufferWrapper> buffers = getBuffersOrderedForOwner(storageLockContext);

    if (!buffers.isEmpty()) {
      List<IOException> exceptions = new SmartList<>();

      mySegmentsAllocationLock.lock();
      try {
        try {
          for (DirectBufferWrapper buffer : buffers.values()) {
            if (buffer.isDirty() && !buffer.isReleased()) {
              buffer.force();
            }
          }
        }
        catch (IOException e) {
          exceptions.add(e);
        }
      }
      finally {
        mySegmentsAllocationLock.unlock();
      }

      if (!exceptions.isEmpty()) {
        throw new IOException(new CompoundRuntimeException(exceptions));
      }
    }
  }

  void invalidateBuffer(int page, @NotNull StorageLockContext storageLockContext) {
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
