// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DapMode {
  /**
   * This mode allows slightly changing the behaviour of the XDebugger when it is run under Debug Adapter Protocol (DAP) mode.
   * In DAP mode XDebugger shouldn't call any UI related code, and it is not allowed to call some services.
   *
   * Important: this registry exists only in Analyzer, and it is always false in Monolith/RemDev etc.
   *
   * @return true if this XDebugger runs in Debug Adapter Protocol (DAP) mode
   */
  @JvmStatic
  fun isDap(): Boolean = Registry.`is`("xdebugger.dap", defaultValue = false)
}