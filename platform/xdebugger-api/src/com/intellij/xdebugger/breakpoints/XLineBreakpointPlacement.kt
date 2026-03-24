// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Describes where a line breakpoint is placed relative to its source line.
 *
 * Placement is owned by the platform and may distinguish multiple line breakpoints that coexist on the same source line.
 * Support for creating [INTER_LINE] breakpoints is provided by [XLineBreakpointType.supportsInterLinePlacement].
 */
@ApiStatus.Internal
@Serializable
enum class XLineBreakpointPlacement {
  /**
   * A breakpoint placed on the source line itself.
   */
  ON_LINE,

  /**
   * A breakpoint placed between source lines.
   *
   * Such a breakpoint is associated with the source line below that gap, so it is rendered above that line.
   * This placement is used only for line breakpoint types whose [XLineBreakpointType.supportsInterLinePlacement] returns `true`.
   */
  INTER_LINE
}
