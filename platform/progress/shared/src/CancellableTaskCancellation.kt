// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class CancellableTaskCancellation private constructor(
  val buttonText: @NlsContexts.Button String?,
  val tooltipText: @NlsContexts.Tooltip String?
) : TaskCancellation.Cancellable {
  override fun withButtonText(@NlsContexts.Button buttonText: String): TaskCancellation.Cancellable {
    return CancellableTaskCancellation(buttonText, this.tooltipText)
  }

  override fun withTooltipText(@NlsContexts.Tooltip tooltipText: String): TaskCancellation.Cancellable {
    return CancellableTaskCancellation(this.buttonText, tooltipText)
  }

  companion object {
    @JvmStatic
    val DEFAULT: TaskCancellation.Cancellable = CancellableTaskCancellation(null, null)
  }
}
