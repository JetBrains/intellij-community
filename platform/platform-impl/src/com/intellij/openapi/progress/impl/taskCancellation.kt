// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.Tooltip

internal object NonCancellableTaskCancellation : TaskCancellation

internal val defaultCancellable: TaskCancellation.Cancellable = CancellableTaskCancellation(null, null)

internal data class CancellableTaskCancellation(
  val buttonText: @Button String?,
  val tooltipText: @Tooltip String?,
) : TaskCancellation.Cancellable {

  override fun withButtonText(buttonText: String): TaskCancellation.Cancellable {
    return copy(buttonText = buttonText)
  }

  override fun withTooltipText(tooltipText: String): TaskCancellation.Cancellable {
    return copy(tooltipText = tooltipText)
  }
}
