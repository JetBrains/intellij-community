// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.io.stats.FilePageCacheStatistics;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

@ApiStatus.Internal
final class FilePageCache {
  private static final Logger LOG = Logger.getInstance(FilePageCache.class);

  static final long MAX_PAGES_COUNT = 0xFFFF_FFFFL;
  private static final long FILE_INDEX_MASK = 0xFFFF_FFFF_0000_0000L;

  private static final int LOWER_LIMIT;
  private static final int UPPER_LIMIT;
  static final int BUFFER_SIZE;

  static {
    final int lower = 100;
    final int upper = CpuArch.is32Bit() ? 200 : 700;

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

  @SuppressWarnings("NonAtomicOperationOnVolatileField") // expected, we don't need 100% precision
  public void incrementUncachedFileAccess() {
    myUncachedFileAccess++;
  }

  public void assertNoBuffersLocked() {
    mySegmentsAllocationLock.lock();
    try {
      mySegmentsAccessLock.lock();
      try {
        for (DirectBufferWrapper value : mySegments.values()) {
          if (value.isLocked()) {
            throw new AssertionError();
          }
        }
        for (DirectBufferWrapper value : mySegmentsToRemove.values()) {
          if (value.isLocked()) {
            throw new AssertionError();
          }
        }
      }
      finally {
        mySegmentsAccessLock.unlock();
      }
    }
    finally {
      mySegmentsAllocationLock.unlock();
    }
  }

  public void incrementHitsCount() {
    myHits++;
  }

  private static long maxDirectMemory() {
    try {
      Class<?> aClass = Class.forName("jdk.internal.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) { }

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

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("MAX_MEMORY");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) { }

    return Runtime.getRuntime().maxMemory();
  }

  @NotNull FilePageCacheStatistics getStatistics() {
    mySegmentsAllocationLock.lock();
    try {
      mySegmentsAccessLock.lock();
      try {
        return new FilePageCacheStatistics(PagedFileStorage.CHANNELS_CACHE.getStatistics(),
                                           myUncachedFileAccess,
                                           myMaxRegisteredFiles,
                                           myHits,
                                           myMisses,
                                           myLoad,
                                           mySizeLimit);
      }
      finally {
        mySegmentsAccessLock.unlock();
      }
    }
    finally {
      mySegmentsAllocationLock.unlock();
    }
  }

  private final Int2ObjectMap<PagedFileStorage> myIndex2Storage = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

  private final LinkedHashMap<Long, DirectBufferWrapper> mySegments;

  private final ReentrantLock mySegmentsAccessLock = new ReentrantLock(); // protects map operations of mySegments, needed for LRU order, mySize and myMappingChangeCount
  // todo avoid locking for access
  private final ReentrantLock mySegmentsAllocationLock = new ReentrantLock();
  private final LinkedHashMap<Long, DirectBufferWrapper> mySegmentsToRemove = new LinkedHashMap<>();

  private final long mySizeLimit;
  private long mySize;
  private volatile int myUncachedFileAccess;
  private int myHits;
  private int myMisses;
  private int myLoad;
  private volatile int myMaxRegisteredFiles;
  private volatile int myMappingChangeCount;

  FilePageCache() {
    mySizeLimit = UPPER_LIMIT;
    mySegments = new LinkedHashMap<Long, DirectBufferWrapper>(10, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Long, DirectBufferWrapper> eldest) {
        assert mySegmentsAccessLock.isHeldByCurrentThread();
        return mySize > mySizeLimit;
      }

      @Override
      public DirectBufferWrapper put(Long key, @NotNull DirectBufferWrapper wrapper) {
        mySize += wrapper.getLength();
        return super.put(key, wrapper);
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
          mySegmentsToRemove.put((Long)key, wrapper);
          mySize -= wrapper.getLength();
        }
        return wrapper;
      }
    };
  }

  int getMappingChangeCount() {
    return myMappingChangeCount;
  }

  long registerPagedFileStorage(@NotNull PagedFileStorage storage) {
    synchronized (myIndex2Storage) {
      int registered = myIndex2Storage.size();
      int value = registered << 16;
      while(myIndex2Storage.get(value) != null) {
        ++registered;
        value = registered << 16;
      }
      myIndex2Storage.put(value, storage);
      myMaxRegisteredFiles = Math.max(myMaxRegisteredFiles, myIndex2Storage.size());
      return (long)value << 32;
    }
  }

  @NotNull("Seems accessed storage has been closed")
  private PagedFileStorage getRegisteredPagedFileStorageByIndex(long key) {
    int storageIndex = (int)((key & FILE_INDEX_MASK) >> 32);
    return myIndex2Storage.get(storageIndex);
  }

  DirectBufferWrapper get(Long key, boolean read) throws IOException {
    DirectBufferWrapper wrapper;
    try {         // fast path
      mySegmentsAccessLock.lock();
      wrapper = mySegments.get(key);
      if (wrapper != null) {
        myHits++;
        return wrapper;
      }
    }
    finally {
      mySegmentsAccessLock.unlock();
    }

    mySegmentsAllocationLock.lock();
    try {
      DirectBufferWrapper notYetRemoved = mySegmentsToRemove.remove(key);
      if (notYetRemoved != null) {
        mySegmentsAccessLock.lock();
        try {
          DirectBufferWrapper previous = mySegments.put(key, notYetRemoved);
          assert previous == null;
        }
        finally {
          mySegmentsAccessLock.unlock();
        }

        disposeRemovedSegments(null);
        myHits++;
        return notYetRemoved;
      }

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

      disposeRemovedSegments(null);

      wrapper = createValue(key, read, fileStorage);

      if (IOStatistics.DEBUG) {
        long finished = System.currentTimeMillis();
        if (finished - started > IOStatistics.MIN_IO_TIME_TO_REPORT) {
          IOStatistics.dump(
            "Mapping " + wrapper + " for " + (finished - started));
        }
      }

      mySegmentsAccessLock.lock();
      try {
        if (mySegments.size() < mySizeLimit) {
          myLoad++;
        }
        else {
          myMisses++;
        }
        mySegments.put(key, wrapper);
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

  private void disposeRemovedSegments(@Nullable PagedFileStorage verificationStorage) {
    assertUnderSegmentAllocationLock();

    if (mySegmentsToRemove.isEmpty()) return;
    Iterator<Map.Entry<Long, DirectBufferWrapper>> iterator = mySegmentsToRemove.entrySet().iterator();
    while (iterator.hasNext()) {
      try {
        Map.Entry<Long, DirectBufferWrapper> entry = iterator.next();
        DirectBufferWrapper wrapper = entry.getValue();
        boolean released = wrapper.tryRelease(wrapper.getFile() == verificationStorage);

        if (released) {
          iterator.remove();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
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

    disposeRemovedSegments(null);
  }

  @NotNull
  private static DirectBufferWrapper createValue(Long key, boolean read, PagedFileStorage owner) throws IOException {
    StorageLockContext context = owner.getStorageLockContext();
    if (read) {
      context.checkReadAccess();
    }
    else {
      context.checkWriteAccess();
    }
    long off = (key & MAX_PAGES_COUNT) * owner.myPageSize;

    return new DirectBufferWrapper(owner, off);
  }


  @NotNull
  private Map<Long, DirectBufferWrapper> getBuffersOrderedForOwner(@NotNull StorageLockContext storageLockContext) {
    mySegmentsAccessLock.lock();
    try {
      storageLockContext.checkReadAccess();
      Map<Long, DirectBufferWrapper> mineBuffers = new TreeMap<>();
      for (Map.Entry<Long, DirectBufferWrapper> entry : mySegments.entrySet()) {
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

  void unmapBuffersForOwner(PagedFileStorage fileStorage) {
    Map<Long, DirectBufferWrapper> buffers = getBuffersOrderedForOwner(fileStorage.getStorageLockContext());

    if (!buffers.isEmpty()) {
      mySegmentsAccessLock.lock();
      try {
        for (Long key : buffers.keySet()) {
          mySegments.remove(key);
        }
      }
      finally {
        mySegmentsAccessLock.unlock();
      }
    }

    mySegmentsAllocationLock.lock();
    try {
      disposeRemovedSegments(fileStorage);
    } finally {
      mySegmentsAllocationLock.unlock();
    }
  }

  void flushBuffers() {
    mySegmentsAccessLock.lock();
    try {
      while (!mySegments.isEmpty()) {
        mySegments.doRemoveEldestEntry();
      }
    }
    finally {
      mySegmentsAccessLock.unlock();
    }

    mySegmentsAllocationLock.lock();
    try {
      disposeRemovedSegments(null);
    } finally {
      mySegmentsAllocationLock.unlock();
    }
  }

  void flushBuffersForOwner(StorageLockContext storageLockContext) throws IOException {
    storageLockContext.checkReadAccess();
    Map<Long, DirectBufferWrapper> buffers = getBuffersOrderedForOwner(storageLockContext);

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

  void removeStorage(long index) {
    myIndex2Storage.remove((int)(index >> 32));
  }
}
