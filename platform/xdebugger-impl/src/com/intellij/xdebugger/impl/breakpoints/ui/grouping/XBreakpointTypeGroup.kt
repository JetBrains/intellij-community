// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.breakpointTypes
import java.lang.Long
import javax.swing.Icon
import kotlin.Boolean
import kotlin.Int
import kotlin.String

class XBreakpointTypeGroup(val breakpointType: XBreakpointType<*, *>) : XBreakpointGroup() {
  override fun getName(): String {
    return breakpointType.getTitle()
  }

  override fun getIcon(isOpen: Boolean): Icon? {
    return breakpointType.getEnabledIcon()
  }

  override fun compareTo(o: XBreakpointGroup): Int {
    if (getName() == o.getName()) {
      return 0
    }
    if (o is XBreakpointTypeGroup) {
      if (o.breakpointType is XLineBreakpointType<*>) {
        if (this.breakpointType is XLineBreakpointType<*>) {
          val res = (o.breakpointType as XLineBreakpointType<*>).getPriority() -
                    this.breakpointType.getPriority()
          if (res != 0) {
            return res
          }
        }
        else {
          // line breakpoints should be on top
          return 1
        }
      }
      else if (this.breakpointType is XLineBreakpointType<*>) {
        return -1
      }
      return Long.compare(indexOfType(this.breakpointType), indexOfType(
        o.breakpointType))
    }
    return -o.compareTo(this)
  }

  companion object {
    private fun indexOfType(type: XBreakpointType<*, *>?): kotlin.Long {
      return breakpointTypes().indexOf(type).orElse(-1)
    }
  }
}
