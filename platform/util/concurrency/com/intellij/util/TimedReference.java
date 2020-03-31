// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public class TimedReference<T> extends Timed<T> {
  public TimedReference(@Nullable Disposable parentDisposable) {
    super(parentDisposable);
  }

  @Nullable
  public synchronized T get() {
    myAccessCount++;
    poll();
    return myT;
  }

  public synchronized void set(@Nullable T t) {
    myAccessCount++;
    poll();
    myT = t;
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static void disposeTimed() {
    Timed.disposeTimed();
  }
}
