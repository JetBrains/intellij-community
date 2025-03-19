// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use coroutines or CompletableFuture.
 */
@Deprecated
public abstract class AsyncFutureFactory {
  public static AsyncFutureFactory getInstance() {
    return ApplicationManager.getApplication().getService(AsyncFutureFactory.class);
  }

  public static @NotNull <V> AsyncFuture<V> wrap(V v) {
    final AsyncFutureResult<V> result = getInstance().createAsyncFutureResult();
    result.set(v);
    return result;
  }

  public static @NotNull <V> AsyncFuture<V> wrapException(Throwable e) {
    final AsyncFutureResult<V> result = getInstance().createAsyncFutureResult();
    result.setException(e);
    return result;
  }

  public abstract @NotNull <V> AsyncFutureResult<V> createAsyncFutureResult();
}
