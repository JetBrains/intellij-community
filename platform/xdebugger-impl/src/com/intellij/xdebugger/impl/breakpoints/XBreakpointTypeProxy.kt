// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.breakpointTypes
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XBreakpointTypeProxy {
  val id: String
  val index: Int
  val title: String
  val enabledIcon: Icon
  val isLineBreakpoint: Boolean

  val isSuspendThreadSupported: Boolean
  val priority: Int?

  class Monolith(val breakpointType: XBreakpointType<*, *>) : XBreakpointTypeProxy {
    override val id: String
      get() = breakpointType.id
    override val index: Int
      get() = breakpointTypes().indexOf(breakpointType).orElse(-1).toInt()
    override val title: String
      get() = breakpointType.title
    override val enabledIcon: Icon
      get() = breakpointType.enabledIcon
    override val isLineBreakpoint: Boolean
      get() = breakpointType is XLineBreakpointType<*>
    override val isSuspendThreadSupported: Boolean
      get() = breakpointType.isSuspendThreadSupported

    override val priority: Int?
      get() = if (breakpointType is XLineBreakpointType<*>) {
        breakpointType.priority
      }
      else {
        null
      }
  }
}