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

package com.intellij.util;

import org.jetbrains.annotations.NonNls;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SpinAllocator can be used for allocating short-live automatic objects of type T.
 * Avoiding reenterable allocations, MAX_SIMULTANEOUS_ALLOCATIONS are concurrently possible.
 */
public class SpinAllocator<T> {

  public static final int MAX_SIMULTANEOUS_ALLOCATIONS = 64;

  public interface ICreator<T> {
    T createInstance();
  }

  public interface IDisposer<T> {
    void disposeInstance(T instance);
  }

  public static class AllocatorExhaustedException extends RuntimeException {
    public AllocatorExhaustedException() {
      //noinspection HardCodedStringLiteral
      super("SpinAllocator has exhausted! Too many threads or you're going to get StackOverflow.");
    }
  }

  public static class AllocatorDisposeException extends RuntimeException {
    public AllocatorDisposeException(@NonNls final String message) {
      super(message);
    }
  }
  
  private final AtomicBoolean[] myEmployed = new AtomicBoolean[MAX_SIMULTANEOUS_ALLOCATIONS];
  private final Object[] myObjects = new Object[MAX_SIMULTANEOUS_ALLOCATIONS];
  protected final ICreator<T> myCreator;
  protected final IDisposer<T> myDisposer;

  public SpinAllocator(ICreator<T> creator, IDisposer<T> disposer) {
    myCreator = creator;
    myDisposer = disposer;
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      myEmployed[i] = new AtomicBoolean(false);
    }
  }

  public T alloc() {
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      if (!myEmployed[i].getAndSet(true)) {
        T result = (T)myObjects[i];
        if (result == null) {
          myObjects[i] = result = myCreator.createInstance();
        }
        return result;
      }
    }
    throw new AllocatorExhaustedException();
  }

  public void dispose(T instance) {
    for (int i = 0; i < MAX_SIMULTANEOUS_ALLOCATIONS; ++i) {
      if (myObjects[i] == instance) {
        if (!myEmployed[i].get()) {
          throw new AllocatorDisposeException("Instance is already disposed.");
        }
        myDisposer.disposeInstance(instance);
        myEmployed[i].set(false);
        return;
      }
    }
    throw new AllocatorDisposeException("Attempt to dispose non-allocated instance.");
  }
}
