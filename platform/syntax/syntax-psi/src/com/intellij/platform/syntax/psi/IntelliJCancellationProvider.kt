// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.util.cancellation.CancellationProviderExtension

internal class IntelliJCancellationProvider : CancellationProviderExtension {
  override fun getCancellationProvider(): CancellationProvider = IJCancellationProvider
}

private object IJCancellationProvider : CancellationProvider {
  override fun checkCancelled() {
    ProgressManager.checkCanceled()
  }
}