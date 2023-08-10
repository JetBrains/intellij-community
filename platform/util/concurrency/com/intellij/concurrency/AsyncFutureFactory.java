// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static <V> AsyncFuture<V> wrap(V v) {
    final AsyncFutureResult<V> result = getInstance().createAsyncFutureResult();
    result.set(v);
    return result;
  }

  @NotNull
  public static <V> AsyncFuture<V> wrapException(Throwable e) {
    final AsyncFutureResult<V> result = getInstance().createAsyncFutureResult();
    result.setException(e);
    return result;
  }

  @NotNull
  public abstract <V> AsyncFutureResult<V> createAsyncFutureResult();
}
