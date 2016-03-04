/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author mike
 */
@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
abstract class Timed<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Timed");
  private static final Map<Timed, Boolean> ourReferences = Collections.synchronizedMap(new WeakHashMap<Timed, Boolean>());
  protected static final int SERVICE_DELAY = 60;

  private int myLastCheckedAccessCount;
  int myAccessCount;
  protected T myT;
  private boolean myPolled;

  protected Timed(@Nullable final Disposable parentDisposable) {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  @Override
  public synchronized void dispose() {
    final Object t = myT;
    myT = null;
    if (t instanceof Disposable) {
      Disposer.dispose((Disposable)t);
    }

    remove();
  }

  protected final void poll() {
    if (!myPolled) {
      ourReferences.put(this, Boolean.TRUE);
      myPolled = true;
    }
  }

  protected final void remove() {
    ourReferences.remove(this);
    myPolled = false;
  }

  protected synchronized boolean isLocked() {
    return false;
  }

  protected synchronized boolean checkLocked() {
    return isLocked();
  }

  static {
    AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        try {
          disposeTimed();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }, SERVICE_DELAY, SERVICE_DELAY, TimeUnit.SECONDS);
  }

  static void disposeTimed() {
    final Timed[] references = ourReferences.keySet().toArray(new Timed[ourReferences.size()]);
    for (Timed timed : references) {
      if (timed == null) continue;
      synchronized (timed) {
        if (timed.myLastCheckedAccessCount == timed.myAccessCount && !timed.checkLocked()) {
          Disposer.dispose(timed);
        }
        else {
          timed.myLastCheckedAccessCount = timed.myAccessCount;
        }
      }
    }
  }
}
