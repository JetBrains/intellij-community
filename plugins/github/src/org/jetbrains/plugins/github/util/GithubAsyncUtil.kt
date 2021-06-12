// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.CompletableFutureUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@Deprecated("Moved to common module")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
object GithubAsyncUtil {

  @Deprecated("Background process value now always drops on PCE")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @JvmStatic
  fun <T> futureOfMutable(futureSupplier: () -> CompletableFuture<T>): CompletableFuture<T> {
    val result = CompletableFuture<T>()
    handleToOtherIfCancelled(futureSupplier, result)
    return result
  }

  private fun <T> handleToOtherIfCancelled(futureSupplier: () -> CompletableFuture<T>, other: CompletableFuture<T>) {
    futureSupplier().handle { result, error ->
      if (error != null) {
        if (CompletableFutureUtil.isCancellation(error)) handleToOtherIfCancelled(futureSupplier, other)
        other.completeExceptionally(error.cause)
      }
      other.complete(result)
    }
  }
}