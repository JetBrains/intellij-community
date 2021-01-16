// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.progress.util;

import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AtomicCancelableLazyValue<T> {
  private static final int TIMEOUT = 50;

  private volatile T myValue;
  private final ReentrantLock myLock = new ReentrantLock();

  @NotNull
  public final T getValue() {
    T curValue = myValue;
    if (curValue != null) {
      return curValue;
    }

    return ProgressIndicatorUtils.computeWithLockAndCheckingCanceled(myLock, TIMEOUT, TimeUnit.MILLISECONDS, () -> {
      T value = myValue;
      if (value == null) {
        RecursionGuard.StackStamp stamp = RecursionManager.markStack();
        value = compute();
        if (stamp.mayCacheNow()) {
          myValue = value;
        }
      }
      return value;
    });
  }

  @NotNull
  protected abstract T compute();

  public boolean isComputed() {
    return myValue != null;
  }

  @NotNull
  public static <T> AtomicCancelableLazyValue<T> createValue(@NotNull NotNullFactory<? extends T> value) {
    return new AtomicCancelableLazyValue<>() {
      @NotNull
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }
}
