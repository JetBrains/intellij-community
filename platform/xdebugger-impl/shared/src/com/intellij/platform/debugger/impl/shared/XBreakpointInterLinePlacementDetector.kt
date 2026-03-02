// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point-based strategy for deciding whether a line breakpoint should be rendered
 * in the inter-line area (between line numbers) instead of on a line.
 */
@ApiStatus.Internal
abstract class XBreakpointInterLinePlacementDetector {
  /**
   * Returns `true` when the given breakpoint should use inter-line placement.
   */
  abstract fun shouldBePlacedBetweenLines(breakpoint: XBreakpointProxy): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<XBreakpointInterLinePlacementDetector>(
      "com.intellij.xdebugger.breakpointInterLinePlacementDetector"
    )

    /**
     * Aggregates all detector implementations and returns `true`
     * when at least one detector opts into inter-line placement.
     */
    fun shouldBePlacedBetweenLines(breakpoint: XBreakpointProxy): Boolean {
      return EP_NAME.extensionList.any { detector ->
        detector.shouldBePlacedBetweenLines(breakpoint)
      }
    }
  }
}
