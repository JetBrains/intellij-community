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

package com.intellij.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public abstract class TimedComputable<T>  extends Timed<T> {
  private int myAcquireCount;

  public TimedComputable(Disposable parentDisposable) {
    super(parentDisposable);
  }

  public synchronized T acquire() {
    myAccessCount++;
    myAcquireCount++;

    if (myT == null) myT = calc();
    poll();
    return myT;
  }

  protected synchronized T getIfCached() {
    return myT;
  }

  public synchronized void release() {
    myAcquireCount--;

    assert myAcquireCount >= 0;
  }

  @Override
  public synchronized void dispose() {
    assert myAcquireCount == 0;
    super.dispose();
  }

  @Override
  protected synchronized boolean isLocked() {
    return myAcquireCount != 0;
  }

  @NotNull
  protected abstract T calc();
}