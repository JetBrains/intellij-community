// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides storage for locks returned from ClassLoader#getClassLoadingLock implementation. The returned locks are stored via weak references,
 * so when they become unreachable the corresponding entries are removed from the map, so class names won't waste the memory.
 */
@ApiStatus.Internal
public final class ClassLoadingLocks<T> {
  private final ConcurrentMap<T, WeakLockReference<T>> myMap = new ConcurrentHashMap<>();
  private final ReferenceQueue<Object> myQueue = new ReferenceQueue<>();

  public @NotNull Object getOrCreateLock(@NotNull T className) {
    WeakLockReference<T> lockReference = myMap.get(className);
    if (lockReference != null) {
      Object lock = lockReference.get();
      if (lock != null) {
        return lock;
      }
    }

    Object newLock = new Object();
    WeakLockReference<T> newRef = new WeakLockReference<>(className, newLock, myQueue);
    while (true) {
      processQueue();
      WeakLockReference<T> oldRef = myMap.putIfAbsent(className, newRef);
      if (oldRef == null) {
        return newLock;
      }
      Object oldLock = oldRef.get();
      if (oldLock != null) {
        return oldLock;
      }
      else if (myMap.replace(className, oldRef, newRef)) {
        return newLock;
      }
    }
  }

  private void processQueue() {
    while (true) {
      @SuppressWarnings("unchecked")
      WeakLockReference<T> ref = (WeakLockReference<T>)myQueue.poll();
      if (ref == null) {
        break;
      }
      myMap.remove(ref.myClassName, ref);
    }
  }

  private static final class WeakLockReference<T> extends WeakReference<Object> {
    final T myClassName;

    private WeakLockReference(@NotNull T className, @NotNull Object lock, @NotNull ReferenceQueue<Object> q) {
      super(lock, q);
      myClassName = className;
    }
  }
}
