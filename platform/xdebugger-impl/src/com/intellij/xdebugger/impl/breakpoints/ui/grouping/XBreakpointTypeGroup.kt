// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import javax.swing.Icon

internal class XBreakpointTypeGroup(private val breakpointType: XBreakpointTypeProxy) : XBreakpointGroup() {
  override fun getName(): String {
    return breakpointType.title
  }

  override fun getIcon(isOpen: Boolean): Icon? {
    return breakpointType.enabledIcon
  }

  override fun compareTo(o: XBreakpointGroup): Int {
    if (name == o.getName()) {
      return 0
    }
    if (o is XBreakpointTypeGroup) {
      if (o.breakpointType is XLineBreakpointTypeProxy) {
        if (breakpointType is XLineBreakpointTypeProxy) {
          val res = (o.breakpointType.priority ?: 0) -
                    (breakpointType.priority ?: 0)
          if (res != 0) {
            return res
          }
        }
        else {
          // line breakpoints should be on top
          return 1
        }
      }
      else if (breakpointType is XLineBreakpointTypeProxy) {
        return -1
      }
      return breakpointType.index.compareTo(o.breakpointType.index)
    }
    return -o.compareTo(this)
  }
}
