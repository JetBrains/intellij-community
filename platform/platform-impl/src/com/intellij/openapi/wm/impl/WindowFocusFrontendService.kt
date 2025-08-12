// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class WindowFocusFrontendService {
  private var isFrontendWindowFocused: Boolean = false

  suspend fun <T> performActionWithFocus(frontendFocused: Boolean, action: (suspend () -> T?)? = null): T? {
    val previousValue = isFrontendWindowFocused
    isFrontendWindowFocused = frontendFocused
    try {
      return action?.invoke()
    }
    finally {
      isFrontendWindowFocused = previousValue
    }
  }

  fun isFrontendFocused(): Boolean = isFrontendWindowFocused

  companion object {
    fun getInstance(): WindowFocusFrontendService = service()
  }
}