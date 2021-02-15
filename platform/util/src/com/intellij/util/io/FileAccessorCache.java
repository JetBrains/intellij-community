// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FileAccessorCache<K, T> implements EqualityPolicy<K> {
  /*@GuardedBy("myCacheLock")*/ private final SLRUMap<K, Handle<T>> myCache;
  /*@GuardedBy("myCacheLock")*/ private final List<T> myElementsToBeDisposed = new ArrayList<>();
  private final Object myCacheLock = new Object();
  private final Object myUpdateLock = new Object();

  public FileAccessorCache(int protectedQueueSize, int probationalQueueSize) {
    myCache = new SLRUMap<K, Handle<T>>(protectedQueueSize, probationalQueueSize, this) {
      @Override
      protected final void onDropFromCache(K key, @NotNull Handle<T> value) {
        value.release();
      }
    };
  }

  protected abstract @NotNull T createAccessor(K key) throws IOException;

  protected abstract void disposeAccessor(@NotNull T fileAccessor) throws IOException;

  public final @NotNull Handle<T> get(K key) {
    Handle<T> cached = getIfCached(key);
    if (cached != null) return cached;

    synchronized (myUpdateLock) {
      cached = getIfCached(key);
      if (cached != null) return cached;
      return createHandle(key);
    }
  }

  private Handle<T> createHandle(K key) {
    try {
      Handle<T> cached = new Handle<>(createAccessor(key), this);
      cached.allocate();

      synchronized (myCacheLock) {
        myCache.put(key, cached);
      }

      disposeInvalidAccessors();
      return cached;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void disposeInvalidAccessors() {
    List<T> fileAccessorsToBeDisposed;
    synchronized (myCacheLock) {
      if (myElementsToBeDisposed.isEmpty()) return;
      fileAccessorsToBeDisposed = new ArrayList<>(myElementsToBeDisposed);
      myElementsToBeDisposed.clear();
    }

    for (T t : fileAccessorsToBeDisposed) {
      try {
        disposeAccessor(t);
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  public Handle<T> getIfCached(K key) {
    synchronized (myCacheLock) {
      final Handle<T> value = myCache.get(key);
      if (value != null) {
        value.allocate();
      }
      return value;
    }
  }

  public boolean remove(K key) {
    try {
      synchronized (myCacheLock) {
        return myCache.remove(key);
      }
    }
    finally {
      synchronized (myUpdateLock) {
        disposeInvalidAccessors();
      }
    }
  }

  public void clear() {
    try {
      synchronized (myCacheLock) {
        myCache.clear();
      }
    }
    finally {
      synchronized (myUpdateLock) {
        disposeInvalidAccessors();
      }
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
    private final FileAccessorCache<?, ? super T> myOwner;
    private final @NotNull T myResource;
    private final AtomicInteger myRefCount = new AtomicInteger(1);

    public Handle(@NotNull T fileAccessor, @NotNull FileAccessorCache<?, ? super T> owner) {
      myResource = fileAccessor;
      myOwner = owner;
    }

    public final void allocate() {
      myRefCount.incrementAndGet();
    }

    public final void release() {
      if (myRefCount.decrementAndGet() == 0) {
        synchronized (myOwner.myCacheLock) {
          myOwner.myElementsToBeDisposed.add(myResource);
        }
      }
    }

    public final int getRefCount() {
      return myRefCount.get();
    }

    @Override
    public final void close() {
      release();
    }

    @Override
    public final @NotNull T get() {
      return myResource;
    }
  }
}
