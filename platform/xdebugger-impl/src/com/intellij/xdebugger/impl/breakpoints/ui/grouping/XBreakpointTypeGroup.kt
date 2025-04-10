// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.breakpointTypes
import javax.swing.Icon

internal class XBreakpointTypeGroup(private val breakpointType: XBreakpointType<*, *>) : XBreakpointGroup() {
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
      if (o.breakpointType is XLineBreakpointType<*>) {
        if (breakpointType is XLineBreakpointType<*>) {
          val res = o.breakpointType.priority -
                    breakpointType.priority
          if (res != 0) {
            return res
          }
        }
        else {
          // line breakpoints should be on top
          return 1
        }
      }
      else if (breakpointType is XLineBreakpointType<*>) {
        return -1
      }
      return indexOfType(breakpointType).compareTo(indexOfType(o.breakpointType))
    }
    return -o.compareTo(this)
  }

  private fun indexOfType(type: XBreakpointType<*, *>?): Long {
    return breakpointTypes().indexOf(type).orElse(-1)
  }
}
