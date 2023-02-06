// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides storage for locks returned from ClassLoader#getClassLoadingLock implementation. The returned locks are stored via weak references,
 * so when they become unreachable the corresponding entries are removed from the map, so class names won't waste the memory.
 */
final class ClassLoadingLocks {
  private final ConcurrentMap<String, WeakLockReference> map = new ConcurrentHashMap<>();
  private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

  public @NotNull Object getOrCreateLock(@NotNull String className) {
    WeakLockReference lockReference = map.get(className);
    if (lockReference != null) {
      Object lock = lockReference.get();
      if (lock != null) {
        return lock;
      }
    }

    Object newLock = new Object();
    WeakLockReference newRef = new WeakLockReference(className, newLock, queue);
    while (true) {
      processQueue();
      WeakLockReference oldRef = map.putIfAbsent(className, newRef);
      if (oldRef == null) {
        return newLock;
      }
      Object oldLock = oldRef.get();
      if (oldLock != null) {
        return oldLock;
      }
      else if (map.replace(className, oldRef, newRef)) {
        return newLock;
      }
    }
  }

  private void processQueue() {
    while (true) {
      WeakLockReference ref = (WeakLockReference)queue.poll();
      if (ref == null) {
        break;
      }
      map.remove(ref.className, ref);
    }
  }

  private static final class WeakLockReference extends WeakReference<Object> {
    final String className;

    private WeakLockReference(@NotNull String className, @NotNull Object lock, @NotNull ReferenceQueue<Object> q) {
      super(lock, q);
      this.className = className;
    }
  }
}
