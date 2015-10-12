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

import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FileAccessorCache<K, T> implements com.intellij.util.containers.hash.EqualityPolicy<K> {
  private final SLRUCache<K, Handle<T>> myCache;
  private final Object myLock = new Object();

  public FileAccessorCache(int protectedQueueSize, int probationalQueueSize) {
    myCache = new SLRUCache<K, Handle<T>>(protectedQueueSize, probationalQueueSize, this) {
      @NotNull
      @Override
      public final Handle<T> createValue(K path) {
        try {
          return new Handle<T>(createAccessor(path), FileAccessorCache.this);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }

      @Override
      protected final void onDropFromCache(K key, Handle<T> value) {
        value.release();
      }
    };
  }

  protected abstract T createAccessor(K key) throws IOException;
  protected abstract void disposeAccessor(T fileAccessor);

  protected void disposeCloseable(Closeable fileAccessor) {
    try {
      fileAccessor.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NotNull
  public final Handle<T> get(K key) {
    synchronized (myLock) {
      final Handle<T> value = myCache.get(key);
      value.allocate();
      return value;
    }
  }

  public Handle<T> getIfCached(K key) {
    synchronized (myLock) {
      final Handle<T> value = myCache.getIfCached(key);
      if (value != null) {
        value.allocate();
      }
      return value;
    }
  }

  public boolean remove(K key) {
    synchronized (myLock) {
      return myCache.remove(key);
    }
  }

  public void clear() {
    synchronized (myLock) {
      myCache.clear();
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

  public static final class Handle<T> {
    private final FileAccessorCache<?, T> myOwner;
    private final T myFileAccessor;
    private final AtomicInteger myRefCount = new AtomicInteger(1);

    public Handle(T fileAccessor, FileAccessorCache<?, T> owner) {
      myFileAccessor = fileAccessor;
      myOwner = owner;
    }

    private void allocate() {
      myRefCount.incrementAndGet();
    }

    public final void release() {
      if (myRefCount.decrementAndGet() == 0) {
        myOwner.disposeAccessor(myFileAccessor);
      }
    }

    public T get() {
      return myFileAccessor;
    }
  }
}
