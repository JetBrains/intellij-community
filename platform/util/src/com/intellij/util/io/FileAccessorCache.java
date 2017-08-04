/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class FileAccessorCache<K, T> implements com.intellij.util.containers.hash.EqualityPolicy<K> {
  /*@GuardedBy("myCacheLock")*/ private final SLRUMap<K, Handle<T>> myCache;
  /*@GuardedBy("myCacheLock")*/ private final List<T> myElementsToBeDisposed = new ArrayList<T>();
  private final Object myCacheLock = new Object();
  private final Object myUpdateLock = new Object();

  public FileAccessorCache(int protectedQueueSize, int probationalQueueSize) {
    myCache = new SLRUMap<K, Handle<T>>(protectedQueueSize, probationalQueueSize, this) {
      @Override
      protected final void onDropFromCache(K key, Handle<T> value) {
        value.release();
      }
    };
  }

  protected abstract T createAccessor(K key) throws IOException;
  protected abstract void disposeAccessor(T fileAccessor) throws IOException;

  @NotNull
  public final Handle<T> get(K key) {
    Handle<T> cached = getIfCached(key);
    if (cached != null) return cached;

    synchronized (myUpdateLock) {
      cached = getIfCached(key);
      if (cached != null) return cached;
      return createHandle(key);
    }
  }

  @NotNull
  private Handle<T> createHandle(K key) {
    Handle<T> cached;
    try {
      cached = new Handle<T>(createAccessor(key), this);
      cached.allocate();

      synchronized (myCacheLock) {
        myCache.put(key, cached);
      }

      disposeInvalidAccessors();
      return cached;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void disposeInvalidAccessors() {
    List<T> fileAccessorsToBeDisposed;
    synchronized (myCacheLock) {
      if (myElementsToBeDisposed.isEmpty()) return;
      fileAccessorsToBeDisposed = new ArrayList<T>(myElementsToBeDisposed);
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
    private final FileAccessorCache<?, T> myOwner;
    
    public Handle(T fileAccessor, FileAccessorCache<?, T> owner) {
      super(fileAccessor);
      myOwner = owner;
    }

    @Override
    protected void disposeResource() {
      synchronized (myOwner.myCacheLock) {
        myOwner.myElementsToBeDisposed.add(get());
      }
    }
  }
}
