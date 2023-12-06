// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  public final @NotNull T getValue() {
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

  protected abstract @NotNull T compute();

  public boolean isComputed() {
    return myValue != null;
  }

  public static @NotNull <T> AtomicCancelableLazyValue<T> createValue(@NotNull NotNullFactory<? extends T> value) {
    return new AtomicCancelableLazyValue<>() {
      @Override
      protected @NotNull T compute() {
        return value.create();
      }
    };
  }
}
