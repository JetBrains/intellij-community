// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface XBreakpointCustomTooltipProvider {
  /**
   * @return a complete HTML tooltip description, or `null` if the provider cannot provide
   * a description for the given breakpoint that is better than the default one.
   */
  fun tryProvide(breakpoint: XBreakpointBase<*, *, *>): @Nls String?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<XBreakpointCustomTooltipProvider> = ExtensionPointName.create("com.intellij.xdebugger.breakpointCustomTooltipProvider")
  }
}
