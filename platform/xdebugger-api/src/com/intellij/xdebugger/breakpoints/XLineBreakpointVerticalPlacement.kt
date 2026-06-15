// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import com.intellij.openapi.util.SystemInfo
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent

/**
 * Describes where a line breakpoint is placed relative to its source line.
 *
 * Placement is owned by the platform and may distinguish multiple line breakpoints that coexist on the same source line.
 * Support for creating [INTER_LINE] breakpoints is provided by [XLineBreakpointType.supportsInterLinePlacement].
 *
 * @param keyModifier a modifier key that is used to toggle the breakpoint on/off. When pressed,
 *   the IDE only suggests placement/removal of a breakpoint of that type.
 */
@ApiStatus.Experimental
@Serializable
enum class XLineBreakpointVerticalPlacement(val keyModifier: Int) {
  /**
   * A breakpoint placed on the source line itself.
   */
  ON_LINE(if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK),

  /**
   * A breakpoint placed between source lines.
   *
   * Such a breakpoint is associated with the source line below that gap, so it is rendered above that line.
   * This placement is used only for line breakpoint types whose [XLineBreakpointType.supportsInterLinePlacement] returns `true`.
   */
  INTER_LINE(InputEvent.SHIFT_DOWN_MASK),
}
