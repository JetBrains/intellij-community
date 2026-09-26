// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

internal data class ToolbarWidthCalculationEvent(val toolbar: MainToolbar)

internal interface ToolbarWidthCalculationListener {
  fun onToolbarCompressed(event: ToolbarWidthCalculationEvent)
}
