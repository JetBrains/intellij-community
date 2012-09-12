/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class FieldCache<T, Owner,AccessorParameter,Parameter> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("fieldCache");
  private final ReentrantReadWriteLock.ReadLock r;
  private final ReentrantReadWriteLock.WriteLock w;

  protected FieldCache() {
    ReentrantReadWriteLock ourLock = new ReentrantReadWriteLock();
    r = ourLock.readLock();
    w = ourLock.writeLock();
  }

  public T get(AccessorParameter a, Owner owner, Parameter p) {
    r.lock();
    T result;
    try {
      result = getValue(owner, a);
    }
    finally {
      r.unlock();
    }

    if (result == null) {
      w.lock();

      try {
        result = getValue(owner, a);
        if (result == null) {
          RecursionGuard.StackStamp stamp = ourGuard.markStack();
          result = compute(owner, p);
          if (stamp.mayCacheNow()) {
            putValue(result, owner, a);
          }
        }
      }
      finally {
        w.unlock();
      }
    }
    return result;
  }

  public final T getCached(AccessorParameter a, Owner owner) {
    r.lock();

    try {
      return getValue(owner, a);
    }
    finally {
      r.unlock();
    }
  }

  public void clear(AccessorParameter a, Owner owner) {
    w.lock();
    try {
      putValue(null, owner, a);
    }
    finally{
      w.unlock();
    }
  }

  protected abstract T compute(Owner owner, Parameter p);
  protected abstract T getValue(Owner owner, AccessorParameter p);
  protected abstract void putValue(T t, Owner owner, AccessorParameter p);
}
