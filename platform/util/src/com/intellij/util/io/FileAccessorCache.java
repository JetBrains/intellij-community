// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FileAccessorCache<K, T> implements EqualityPolicy<K> {

  /** Accessor caches are usually not contended until really high #CPUs */
  private static final int SEGMENTS_COUNT = Math.max(
    Runtime.getRuntime().availableProcessors() / 8,
    1
  );

  /*@GuardedBy("cacheLock")*/ private final SLRUMap<K, Handle<T>> cache;
  /*@GuardedBy("cacheLock")*/ private final List<Handle<T>> handlersToBeDisposed = new ArrayList<>();

  /**
   * Guards .cache and .elementsToBeDisposed structures.
   * Handlers are added to .elementsToBeDisposed inside cache.onDropFromCache(), so it is already under cacheLock, which makes
   * it natural to use the same cacheLock for cleaning .elementsToBeProcessed in {@link #runPostponedDisposals()}
   */
  private final ReentrantLock cacheLock = new ReentrantLock();

  /**
   * Guards allocation of new resource/{@link Handle}, and its disposal.
   * Lock is segmented by [key.hash % SEGMENTS_COUNT]
   */
  private final ReentrantLock[] resourceAllocationLocks = new ReentrantLock[SEGMENTS_COUNT];

  public FileAccessorCache(int protectedQueueSize,
                           int probationalQueueSize) {
    cache = new SLRUMap<K, Handle<T>>(protectedQueueSize, probationalQueueSize, this) {
      @Override
      protected void onDropFromCache(K key, @NotNull Handle<T> value) {
        value.release();
      }
    };
    for (int i = 0; i < resourceAllocationLocks.length; i++) {
      resourceAllocationLocks[i] = new ReentrantLock();
    }
  }

  protected abstract @NotNull T createAccessor(K key) throws IOException;

  protected abstract void disposeAccessor(@NotNull T fileAccessor) throws IOException;

  public final @NotNull Handle<T> get(K key) {
    Handle<T> cached = getIfCached(key);
    if (cached != null) return cached;

    try {
      return withUpdateLock(key, () -> {
        Handle<T> _cached = getIfCached(key);
        if (_cached != null) return _cached;

        return createHandle(key);
      });
    }
    finally {
      runPostponedDisposals();
    }
  }

  //GuardedBy("updateLock[key]")
  private Handle<T> createHandle(K key) {
    try {
      Handle<T> newHandle = new Handle<>(key, createAccessor(key), this);
      //Handle initially has refCount=2: +1 in ctor, and +1 here. This is because we don't want Handler to be disposed
      // right after all current users release it, we want it to remain in cache, if cache has capacity for it.
      // So we dispose a Handler when it is (not currently in use) AND (evicted from cache). So +1 refCount in ctor
      // could be interpreted as 'a reference from the cache itself'
      newHandle.allocate();

      cacheLock.lock();
      try {
        cache.put(key, newHandle);
      }
      finally {
        cacheLock.unlock();
      }
      return newHandle;
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private void runPostponedDisposals() {
    List<Handle<T>> handlesToBeDisposed;
    cacheLock.lock();
    try {
      if (handlersToBeDisposed.isEmpty()) return;
      handlesToBeDisposed = new ArrayList<>(handlersToBeDisposed);
      handlersToBeDisposed.clear();
    }
    finally {
      cacheLock.unlock();
    }

    IOException disposeException = null;
    for (Handle<T> handleToDispose : handlesToBeDisposed) {
      try {
        //noinspection unchecked
        withUpdateLock((K)handleToDispose.key, () -> {
          disposeAccessor(handleToDispose.resource);
          return null;
        });
      }
      catch (IOException ex) {
        //postpone throwing an exception -- try to dispose as many handlers, as possible
        if (disposeException == null) {
          disposeException = ex;
        }
        else {
          disposeException.addSuppressed(ex);
        }
      }
    }

    if (disposeException != null) {
      throw new UncheckedIOException(disposeException);
    }
  }

  private <R, E extends Exception> R withUpdateLock(@NotNull K key,
                                                    @NotNull ThrowableComputable<R, E> lambda) throws E {
    int hash = getHashCode(key);
    int segmentNo = Math.abs(hash) % resourceAllocationLocks.length;
    ReentrantLock lock = resourceAllocationLocks[segmentNo];
    lock.lock();
    try {
      return lambda.compute();
    }
    finally {
      lock.unlock();
    }
  }

  public Handle<T> getIfCached(K key) {
    cacheLock.lock();
    try {
      final Handle<T> value = cache.get(key);
      if (value != null) {
        value.allocate();
      }
      return value;
    }
    finally {
      cacheLock.unlock();
    }
  }

  public boolean remove(K key) {
    try {
      cacheLock.lock();
      try {
        return cache.remove(key);
      }
      finally {
        cacheLock.unlock();
      }
    }
    finally {
      runPostponedDisposals();
    }
  }

  public void clear() {
    try {
      cacheLock.lock();
      try {
        cache.clear();
      }
      finally {
        cacheLock.unlock();
      }
    }
    finally {
      runPostponedDisposals();
    }
  }

  @Override
  public int getHashCode(K value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(K val1, K val2) {
    return val1.equals(val2);
  }

  public static final class Handle<T> extends ResourceHandle<T> {
    private final Object key;
    private final FileAccessorCache<?, T> owner;
    private final @NotNull T resource;
    private final AtomicInteger refCount = new AtomicInteger(1);

    private <K> Handle(@NotNull K key,
                       @NotNull T fileAccessor,
                       @NotNull FileAccessorCache<K, T> owner) {
      this.key = key;
      this.resource = fileAccessor;
      this.owner = owner;
    }

    public void allocate() {
      refCount.incrementAndGet();
    }

    public void release() {
      if (refCount.decrementAndGet() == 0) {
        owner.cacheLock.lock();
        try {
          owner.handlersToBeDisposed.add(this);
        }
        finally {
          owner.cacheLock.unlock();
        }
      }
    }

    public int getRefCount() {
      return refCount.get();
    }

    @Override
    public void close() {
      release();
    }

    @Override
    public @NotNull T get() {
      return resource;
    }
  }
}
