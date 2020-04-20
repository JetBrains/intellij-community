// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

object GHPRDataProviderUtil {
  @Throws(ProcessCanceledException::class)
  fun <T> CompletableFuture<T>.joinCancellable(): T {
    try {
      return join()
    }
    catch (e: CancellationException) {
      throw ProcessCanceledException(e)
    }
    catch (e: CompletionException) {
      if (GithubAsyncUtil.isCancellation(e)) throw ProcessCanceledException(e)
      throw GithubAsyncUtil.extractError(e)
    }
  }

  fun <T> futureOfMutableOnEDT(futureSupplier: () -> CompletableFuture<T>) = GithubAsyncUtil.futureOfMutable { invokeAndWaitIfNeeded { futureSupplier() } }

  fun <T> futureOfMutableOnEDT(progressIndicator: ProgressIndicator, futureSupplier: () -> CompletableFuture<T>) =
    GithubAsyncUtil.futureOfMutable {
      progressIndicator.checkCanceled()
      invokeAndWaitIfNeeded { futureSupplier() }
    }
}