// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.StandardProgressIndicator
import com.intellij.openapi.progress.WrappedProgressIndicator
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.CancellationTokenSource
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleProjectResolverIndicator(
  private val progressIndicator: ProgressIndicator,
  private val cancellationTokenSource: CancellationTokenSource
) : CancellationTokenSource,
    ProgressIndicator by progressIndicator,
    StandardProgressIndicator,
    WrappedProgressIndicator {

  override fun getOriginalProgressIndicator(): ProgressIndicator {
    return progressIndicator
  }

  override fun isCanceled(): Boolean {
    syncCancellationState()
    return progressIndicator.isCanceled()
  }

  override fun cancel() {
    if (!isCanceled) {
      progressIndicator.cancel()
      cancellationTokenSource.cancel()
    }
  }

  override fun checkCanceled() {
    syncCancellationState()
    progressIndicator.checkCanceled()
  }

  override fun token(): CancellationToken {
    return cancellationTokenSource.token()
  }

  private fun syncCancellationState() {
    val isTokenCancelled = cancellationTokenSource.token().isCancellationRequested
    val isIndicatorCancelled = progressIndicator.isCanceled
    if (isIndicatorCancelled && !isTokenCancelled) {
      cancellationTokenSource.cancel()
    }
    if (isTokenCancelled && !isIndicatorCancelled) {
      progressIndicator.cancel()
    }
  }
}