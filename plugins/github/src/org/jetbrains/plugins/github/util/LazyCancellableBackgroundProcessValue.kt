// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Computable
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

abstract class LazyCancellableBackgroundProcessValue<T>(private val progressManager: ProgressManager)
  : ClearableLazyValue<CompletableFuture<T>>() {
  private var progressIndicator = NonReusableEmptyProgressIndicator()

  override fun compute(): CompletableFuture<T> {
    progressIndicator = NonReusableEmptyProgressIndicator()
    val indicator = progressIndicator
    return CompletableFuture.supplyAsync(Supplier { progressManager.runProcess(Computable { compute(indicator) }, indicator) },
                                         ProcessIOExecutorService.INSTANCE)
  }

  abstract fun compute(indicator: ProgressIndicator): T

  override fun drop() {
    super.drop()
    progressIndicator.cancel()
  }

  companion object {
    fun <T> create(progressManager: ProgressManager,
                   computer: (ProgressIndicator) -> T) = object : LazyCancellableBackgroundProcessValue<T>(progressManager) {
      override fun compute(indicator: ProgressIndicator) = computer(indicator)
    }
  }
}