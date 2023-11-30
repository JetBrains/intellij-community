// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object IOCancellationCallbackHolder {
  // not volatile - ok without it
  private var usedIoCallback: IOCancellationCallback = object : IOCancellationCallback {
    override fun checkCancelled() {
    }

    override fun interactWithUI() {
    }
  }

  fun setIoCancellationCallback(callback: IOCancellationCallback) {
    usedIoCallback = callback
  }

  @JvmStatic
  fun checkCancelled() {
    usedIoCallback.checkCancelled()
  }

  fun interactWithUI() {
    usedIoCallback.interactWithUI()
  }
}