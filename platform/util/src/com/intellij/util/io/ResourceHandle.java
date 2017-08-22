/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ResourceHandle<T> implements Closeable {
  private final T myResource;
  private final AtomicInteger myRefCount = new AtomicInteger(1);

  public ResourceHandle(T resource) {
    myResource = resource;
  }

  public void allocate() {
    myRefCount.incrementAndGet();
  }

  public final void release() {
    if (myRefCount.decrementAndGet() == 0) {
      disposeResource();
    }
  }

  public T get() {
    return myResource;
  }

  public int getRefCount() {
    return myRefCount.get();
  }

  protected abstract void disposeResource();

  @Override
  public void close() {
    release();
  }
}
