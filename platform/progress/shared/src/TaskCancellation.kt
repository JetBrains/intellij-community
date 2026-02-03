// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Contract

@Serializable
sealed interface TaskCancellation {

  @Serializable
  sealed interface NonCancellable : TaskCancellation

  @Serializable
  sealed interface Cancellable : TaskCancellation {
    @Contract(value = "_ -> new", pure = true)
    fun withButtonText(buttonText: @NlsContexts.Button String): Cancellable

    @Contract(value = "_ -> new", pure = true)
    fun withTooltipText(tooltipText: @NlsContexts.Tooltip String): Cancellable
  }

  companion object {
    /**
     * @return a cancellation instance, which means that the cancel button should not be displayed in the UI
     *
     * It does not indicate that the task is inside [kotlinx.coroutines.NonCancellable] section.
     */
    @Contract(pure = true)
    @JvmStatic
    fun nonCancellable(): NonCancellable {
      return NonCancellableTaskCancellation
    }

    /**
     * The returned instance can optionally be customized with button text and/or tooltip text.
     * If [the button text][Cancellable.withButtonText] is not specified,
     * then [the default text][com.intellij.CommonBundle.getCancelButtonText] is used.
     *
     * @return a cancellation instance, which means that the cancel button should be displayed in the UI
     */
    @Contract(pure = true)
    @JvmStatic
    fun cancellable(): Cancellable {
      return CancellableTaskCancellation.DEFAULT
    }
  }
}
