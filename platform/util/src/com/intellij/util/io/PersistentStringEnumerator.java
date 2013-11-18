/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jsr166e.extra.SequenceLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;

public class PersistentStringEnumerator extends PersistentEnumeratorDelegate<String> implements AbstractStringEnumerator {
  private static final int STRIPE_POWER = 4;
  private static final int STRIPE_COUNT = 1 << STRIPE_POWER;
  private static final int STRIPE_MASK = STRIPE_COUNT - 1;
  @Nullable private final SLRUMap<Integer, Integer>[] myHashcodeToIdCache;
  @Nullable private final SLRUMap<Integer, String>[] myIdToStringCache;
  @Nullable private final Lock[] myStripeLocks;

  public PersistentStringEnumerator(@NotNull final File file) throws IOException {
    this(file, null);
  }

  public PersistentStringEnumerator(@NotNull final File file, @Nullable PagedFileStorage.StorageLockContext storageLockContext) throws IOException {
    this(file, 1024 * 4, storageLockContext);
  }

  public PersistentStringEnumerator(@NotNull final File file, boolean cacheLastMappings) throws IOException {
    this(file, 1024 * 4, cacheLastMappings, null);
  }

  public PersistentStringEnumerator(@NotNull final File file, final int initialSize) throws IOException {
    this(file, initialSize, null);
  }

  public PersistentStringEnumerator(@NotNull final File file,
                                    final int initialSize,
                                    @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
    this(file, initialSize, false, lockContext);
  }

  private PersistentStringEnumerator(@NotNull final File file,
                                     final int initialSize,
                                     boolean cacheLastMappings,
                                     @Nullable PagedFileStorage.StorageLockContext lockContext) throws IOException {
    super(file, new EnumeratorStringDescriptor(), initialSize, lockContext);
    if (cacheLastMappings) {
      myIdToStringCache = new SLRUMap[STRIPE_COUNT];
      myHashcodeToIdCache = new SLRUMap[STRIPE_COUNT];
      myStripeLocks = new Lock[STRIPE_COUNT];
      int protectedSize = 8192;
      int probationalSize = 8192;

      for(int i = 0; i < STRIPE_COUNT; ++i) {
        myHashcodeToIdCache[i] = new SLRUMap<Integer, Integer>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
        myIdToStringCache[i] = new SLRUMap<Integer, String>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
        myStripeLocks[i] = new SequenceLock();
      }
    } else {
      myIdToStringCache = null;
      myHashcodeToIdCache = null;
      myStripeLocks = null;
    }
  }

  @Override
  public int enumerate(@Nullable String value) throws IOException {
    int valueHashCode =-1;
    int stripe = -1;

    if (myHashcodeToIdCache != null && value != null) {
      valueHashCode = value.hashCode();
      stripe = Math.abs(valueHashCode) & STRIPE_MASK;

      Integer cachedId;

      myStripeLocks[stripe].lock();
      try {
        cachedId = myHashcodeToIdCache[stripe].get(valueHashCode);
      }
      finally {
        myStripeLocks[stripe].unlock();
      }

      if (cachedId != null) {
        int stripe2 = idStripe(cachedId.intValue());
        myStripeLocks[stripe2].lock();
        try {
          String s = myIdToStringCache[stripe2].get(cachedId);
          if (s != null && value.equals(s)) return cachedId.intValue();
        }
        finally {
          myStripeLocks[stripe2].unlock();
        }
      }
    }

    int enumerate = super.enumerate(value);

    if (stripe != -1) {
      Integer enumeratedInteger;

      myStripeLocks[stripe].lock();
      try {
        enumeratedInteger = enumerate;
        myHashcodeToIdCache[stripe].put(valueHashCode, enumeratedInteger);
      } finally {
        myStripeLocks[stripe].unlock();
      }

      int stripe2 = idStripe(enumerate);
      myStripeLocks[stripe2].lock();
      try {
        myIdToStringCache[stripe2].put(enumeratedInteger, value);
      } finally {
        myStripeLocks[stripe2].unlock();
      }
    }

    return enumerate;
  }

  private int idStripe(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return Math.abs(h ^ (h >>> 7) ^ (h >>> 4)) & STRIPE_MASK;
  }

  @Nullable
  @Override
  public String valueOf(int idx) throws IOException {
    int stripe = -1;
    if (myIdToStringCache != null) {
      stripe = idStripe(idx);
      myStripeLocks[stripe].lock();
      try {
        String s = myIdToStringCache[stripe].get(idx);
        if (s != null) return s;
      }
      finally {
        myStripeLocks[stripe].unlock();
      }
    }
    String s = super.valueOf(idx);

    if (stripe != -1 && s != null) {
      myStripeLocks[stripe].lock();
      try {
        myIdToStringCache[stripe].put(idx, s);
      }
      finally {
        myStripeLocks[stripe].unlock();
      }
    }
    return s;
  }

  @Override
  public void close() throws IOException {
    super.close();

    if (myIdToStringCache != null) {
      for(int i = 0; i < myIdToStringCache.length; ++i) {
        myStripeLocks[i].lock();
        myIdToStringCache[i].clear();
        myHashcodeToIdCache[i].clear();
        myStripeLocks[i].unlock();
      }
    }
  }

  @Override
  public void markCorrupted() {
    myEnumerator.markCorrupted();
  }
}
