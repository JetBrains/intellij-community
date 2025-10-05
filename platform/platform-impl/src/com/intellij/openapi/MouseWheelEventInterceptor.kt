// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseWheelEvent

@ApiStatus.Internal
interface MouseWheelEventInterceptor {
  companion object {
    val MOUSE_WHEEL_EVENT_INTERCEPTORS: ExtensionPointName<MouseWheelEventInterceptor> =
      ExtensionPointName.create("com.intellij.mouseWheelEventProcessor")
  }

  fun process(event: MouseWheelEvent): Boolean
}
