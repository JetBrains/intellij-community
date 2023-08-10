// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use coroutines or CompletableFuture.
 */
@Deprecated
public interface AsyncFutureResult<V> extends AsyncFuture<V> {
  void set(V value);
  void setException(@NotNull Throwable t);
}
