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
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantLock;

@Deprecated // use one of com.intellij.util.containers.Concurrent* class instead
public final class StripedReentrantLocks extends StripedLockHolder<ReentrantLock> {
  private StripedReentrantLocks() {
    super(ReentrantLock.class);
  }

  @NotNull
  @Override
  protected ReentrantLock create() {
    return new ReentrantLock();
  }

  private static final StripedReentrantLocks INSTANCE = new StripedReentrantLocks();
  public static StripedReentrantLocks getInstance() {
    return INSTANCE;
  }

  public void lock(int index) {
    ourLocks[index].lock();
  }
  public void unlock(int index) {
    ourLocks[index].unlock();
  }
}
