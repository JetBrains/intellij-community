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
package org.jetbrains.debugger.values;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Vm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main idea of this class - don't create value for remote value handle if already exists. So,
 * implementation of this class keep map of value to remote value handle.
 * Also, this class maintains cache timestamp.
 *
 * Currently WIP implementation doesn't keep such map due to protocol issue. But V8 does.
 */
public abstract class ValueManager<VM extends Vm> {
  public static final Promise<?> OBSOLETE_CONTEXT_PROMISE = Promise.reject(AsyncPromise.OBSOLETE_ERROR);

  private final AtomicInteger cacheStamp = new AtomicInteger();
  private volatile boolean obsolete;

  protected final VM vm;

  protected ValueManager(VM vm) {
    this.vm = vm;
  }

  public void clearCaches() {
    cacheStamp.incrementAndGet();
  }

  @NotNull
  public Function getClearCachesTask() {
    return new Function<Object, Void>() {
      @Override
      public Void fun(Object o) {
        clearCaches();
        return null;
      }
    };
  }

  public final int getCacheStamp() {
    return cacheStamp.get();
  }

  public final boolean isObsolete() {
    return obsolete;
  }

  public final void markObsolete() {
    obsolete = true;
  }

  @NotNull
  public static <T> Promise<T> reject() {
    //noinspection unchecked
    return (Promise<T>)OBSOLETE_CONTEXT_PROMISE;
  }

  @NotNull
  public VM getVm() {
    return vm;
  }
}