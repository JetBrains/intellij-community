// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class RecentStringInterner {
  private final int myStripeMask;
  private final SLRUCache<String, String>[] myInterns;
  private final Lock[] myStripeLocks;

  public RecentStringInterner(@NotNull Disposable parentDisposable) {
    final int stripes = 16;
    //noinspection unchecked
    myInterns = new SLRUCache[stripes];
    myStripeLocks = new Lock[myInterns.length];
    int capacity = 8192;
    for(int i = 0; i < myInterns.length; ++i) {
      myInterns[i] = new SLRUCache<String, String>(capacity / stripes, capacity / stripes) {
        @Override
        public @NotNull String createValue(String key) {
          return key;
        }

        @Override
        protected void putToProtectedQueue(String key, @NotNull String value) {
          super.putToProtectedQueue(value, value);
        }
      };
      myStripeLocks[i] = new ReentrantLock();
    }

    assert Integer.highestOneBit(stripes) == stripes;
    myStripeMask = stripes - 1;
    LowMemoryWatcher.register(this::clear, parentDisposable);
  }

  @Contract("null -> null")
  public @Nullable String get(@Nullable String s) {
    if (s == null) return null;
    final int stripe = Math.abs(s.hashCode()) & myStripeMask;
    myStripeLocks[stripe].lock();
    try {
      return myInterns[stripe].get(s);
    }
    finally {
      myStripeLocks[stripe].unlock();
    }
  }

  public void clear() {
    for(int i = 0; i < myInterns.length; ++i) {
      myStripeLocks[i].lock();
      try {
        myInterns[i].clear();
      }
      finally {
        myStripeLocks[i].unlock();
      }
    }
  }
}
