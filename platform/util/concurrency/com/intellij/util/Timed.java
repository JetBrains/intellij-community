// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
abstract class Timed<T> implements Disposable {
  private static final Logger LOG = Logger.getInstance(Timed.class);
  private static final Map<Timed, Boolean> ourReferences = Collections.synchronizedMap(new WeakHashMap<>());
  protected static final int SERVICE_DELAY = 60;

  private int myLastCheckedAccessCount;
  int myAccessCount;
  protected T myT;
  private boolean myPolled;

  Timed(@Nullable final Disposable parentDisposable) {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  @Override
  public synchronized void dispose() {
    final Object t = myT;
    myT = null;
    if (t instanceof Disposable && !Disposer.isDisposed((Disposable) t)) {
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
    myLastCheckedAccessCount = 0;
    myAccessCount = 0;
  }

  protected synchronized boolean isLocked() {
    return false;
  }

  protected synchronized boolean checkLocked() {
    return isLocked();
  }

  static {
    AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
      try {
        disposeTimed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }, SERVICE_DELAY, SERVICE_DELAY, TimeUnit.SECONDS);
  }

  public synchronized boolean isCached() {
    return myT != null;
  }

  static void disposeTimed() {
    Timed<?>[] references = ourReferences.keySet().toArray(new Timed[0]);
    for (Timed<?> timed : references) {
      if (timed == null) {
        continue;
      }
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
