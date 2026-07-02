// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerMode {
  @Deprecated("Debugger split flag is enabled by default", replaceWith = ReplaceWith("true"))
  @JvmStatic
  fun isSplitDebugger(): Boolean = true

  @JvmStatic
  fun showSplitWarnings(): Boolean = Registry.`is`("xdebugger.toolwindow.split.warnings")
}
