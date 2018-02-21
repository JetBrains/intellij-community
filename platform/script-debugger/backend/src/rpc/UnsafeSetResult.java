// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.rpc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

// we cannot fix all WIP types to be nullable for now,
// but don't want to to use explicitly nullable result type for method setResult
class UnsafeSetResult {
  static <T> void setResult(@NotNull AsyncPromise<T> promise, @Nullable Object result) {
    //noinspection unchecked
    promise.setResult((T)result);
  }
}
