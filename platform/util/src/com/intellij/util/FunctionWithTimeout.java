// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 *
 * @deprecated consider another approach to execute runnable on a background thread
 * @see com.intellij.openapi.application.Application#executeOnPooledThread(java.util.concurrent.Callable)
 * @see com.intellij.openapi.progress.util.ProgressIndicatorUtils#withTimeout(long, com.intellij.openapi.util.Computable)
 * @see com.intellij.util.concurrency.AppExecutorUtil#getAppScheduledExecutorService()
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
public abstract class FunctionWithTimeout<T> {
  protected abstract void updateValue(T initialValue);

  @NotNull
  public T calculate(long timeout, @NotNull final T initialValue) {
    TimeoutUtil.executeWithTimeout(timeout, () -> updateValue(initialValue));
    return initialValue;
  }
}
