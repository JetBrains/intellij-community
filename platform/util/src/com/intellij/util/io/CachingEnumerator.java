/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

/**
 * @author peter
 */
public class CachingEnumerator<Data> implements DataEnumerator<Data> {
  private static final int STRIPE_POWER = 4;
  private static final int STRIPE_COUNT = 1 << STRIPE_POWER;
  private static final int STRIPE_MASK = STRIPE_COUNT - 1;
  @SuppressWarnings("unchecked") private final SLRUMap<Integer, Integer>[] myHashcodeToIdCache = new SLRUMap[STRIPE_COUNT];
  @SuppressWarnings("unchecked") private final SLRUMap<Integer, Data>[] myIdToStringCache = new SLRUMap[STRIPE_COUNT];
  private final Lock[] myStripeLocks = new Lock[STRIPE_COUNT];
  private final DataEnumerator<Data> myBase;
  private final KeyDescriptor<Data> myDataDescriptor;

  public CachingEnumerator(DataEnumerator<Data> base, KeyDescriptor<Data> dataDescriptor) {
    myBase = base;
    myDataDescriptor = dataDescriptor;
    int protectedSize = 8192;
    int probationalSize = 8192;

    for(int i = 0; i < STRIPE_COUNT; ++i) {
      myHashcodeToIdCache[i] = new SLRUMap<Integer, Integer>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
      myIdToStringCache[i] = new SLRUMap<Integer, Data>(protectedSize / STRIPE_COUNT, probationalSize / STRIPE_COUNT);
      myStripeLocks[i] = new SequenceLock();
    }

  }

  public int enumerate(@Nullable Data value) throws IOException {
    int valueHashCode =-1;
    int stripe = -1;

    if (value != null) {
      valueHashCode = myDataDescriptor.getHashCode(value);
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
          Data s = myIdToStringCache[stripe2].get(cachedId);
          if (s != null && myDataDescriptor.isEqual(value, s)) return cachedId.intValue();
        }
        finally {
          myStripeLocks[stripe2].unlock();
        }
      }
    }

    int enumerate = myBase.enumerate(value);

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

  private static int idStripe(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return Math.abs(h ^ (h >>> 7) ^ (h >>> 4)) & STRIPE_MASK;
  }

  @Nullable
  public Data valueOf(int idx) throws IOException {
    int stripe = idStripe(idx);
    myStripeLocks[stripe].lock();
    try {
      Data s = myIdToStringCache[stripe].get(idx);
      if (s != null) return s;
    }
    finally {
      myStripeLocks[stripe].unlock();
    }

    Data s = myBase.valueOf(idx);

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

  public void close() {
    clear();
  }

  public void clear() {
    for(int i = 0; i < myIdToStringCache.length; ++i) {
      myStripeLocks[i].lock();
      myIdToStringCache[i].clear();
      myHashcodeToIdCache[i].clear();
      myStripeLocks[i].unlock();
    }
  }
}
