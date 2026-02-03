// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class CachingEnumerator<Data> implements DataEnumerator<Data> {
  private static final int STRIPE_POWER = 4;
  private static final int STRIPE_COUNT = 1 << STRIPE_POWER;
  private static final int STRIPE_MASK = STRIPE_COUNT - 1;

  @SuppressWarnings("unchecked") private final SLRUMap<Integer, Integer>[] myHashcodeToIdCache = new SLRUMap[STRIPE_COUNT];
  @SuppressWarnings("unchecked") private final SLRUMap<Integer, Data>[] myIdToStringCache = new SLRUMap[STRIPE_COUNT];
  private final Lock[] myStripeLocks = new Lock[STRIPE_COUNT];

  private final DataEnumerator<Data> myBase;
  private final EqualityPolicy<Data> myDataDescriptor;

  public CachingEnumerator(@NotNull DataEnumerator<Data> base, @NotNull EqualityPolicy<Data> dataDescriptor) {
    myBase = base;
    myDataDescriptor = dataDescriptor;
    int protectedSize = 8192;
    int probationalSize = 8192;

    for(int i = 0; i < STRIPE_COUNT; ++i) {
      myHashcodeToIdCache[i] = new SLRUMap<>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
      myIdToStringCache[i] = new SLRUMap<>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
      myStripeLocks[i] = new ReentrantLock();
    }
  }

  @Override
  public int enumerate(@Nullable Data value) throws IOException {
    int valueHashCode;
    int stripe;
    if (value == null) {
      valueHashCode = -1;
      stripe = -1;
    }
    else {
      valueHashCode = myDataDescriptor.getHashCode(value);
      stripe = Math.abs(valueHashCode) & STRIPE_MASK;
    }

    Lock lock1 = null;
    if (value != null) {
      lock1 = myStripeLocks[stripe];
      lock1.lock();
      Integer cachedId;
      try {
        cachedId = myHashcodeToIdCache[stripe].get(valueHashCode);
      }
      finally {
        lock1.unlock();
      }

      if (cachedId != null) {
        int stripe2 = idStripe(cachedId.intValue());
        Lock lock2 = myStripeLocks[stripe2];
        lock2.lock();
        try {
          Data s = myIdToStringCache[stripe2].get(cachedId);
          if (s != null && myDataDescriptor.isEqual(value, s)) return cachedId.intValue();
        }
        finally {
          lock2.unlock();
        }
      }
    }

    int enumerate = myBase.enumerate(value);

    if (stripe != -1) {
      lock1.lock();
      Integer enumeratedInteger;
      try {
        enumeratedInteger = enumerate;
        myHashcodeToIdCache[stripe].put(valueHashCode, enumeratedInteger);
      }
      finally {
        lock1.unlock();
      }

      int stripe2 = idStripe(enumerate);
      Lock lock2 = myStripeLocks[stripe2];
      lock2.lock();
      try {
        myIdToStringCache[stripe2].put(enumeratedInteger, value);
      }
      finally {
        lock2.unlock();
      }
    }

    return enumerate;
  }

  private static int idStripe(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return Math.abs(h ^ (h >>> 7) ^ (h >>> 4)) & STRIPE_MASK;
  }

  @Override
  public @Nullable Data valueOf(int idx) throws IOException {
    int stripe = idStripe(idx);
    Lock lock = myStripeLocks[stripe];
    lock.lock();
    try {
      Data s = myIdToStringCache[stripe].get(idx);
      if (s != null) return s;
    }
    finally {
      lock.unlock();
    }

    Data s = myBase.valueOf(idx);

    if (s != null) {
      lock.lock();
      try {
        myIdToStringCache[stripe].put(idx, s);
      }
      finally {
        lock.unlock();
      }
    }
    return s;
  }

  void close() {
    clear();
  }

  private void clear() {
    for(int i = 0; i < myIdToStringCache.length; ++i) {
      myStripeLocks[i].lock();
      try {
        myIdToStringCache[i].clear();
        myHashcodeToIdCache[i].clear();
      }
      finally {
        myStripeLocks[i].unlock();
      }
    }
  }
}
