// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.progress

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JfrCancellationEventsCallbackHolder {

  @Volatile
  private var callback: JfrCancellationEventCallback? = null

  fun setCallback(callback: JfrCancellationEventCallback) {
    this.callback = callback
  }

  @JvmStatic
  fun nonCancellableSectionInvoked() {
    callback?.nonCanceledSectionInvoked()
  }

  @JvmStatic
  fun cancellableSectionInvoked(wasCanceled: Boolean) {
    callback?.cancellableSectionInvoked(wasCanceled)
  }
}