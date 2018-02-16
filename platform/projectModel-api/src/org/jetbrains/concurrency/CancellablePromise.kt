// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import java.util.concurrent.Future

interface CancellablePromise<T> : Promise<T>, Future<T> {
  /**
   * The same as @{link Future{@link Future#isDone()}}.
   * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method will return true.
   */
  override fun isDone() = state != Promise.State.PENDING

  fun cancel()
}