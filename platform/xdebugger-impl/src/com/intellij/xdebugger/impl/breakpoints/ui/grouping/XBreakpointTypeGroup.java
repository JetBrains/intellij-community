// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping;

import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class XBreakpointTypeGroup extends XBreakpointGroup {

  private final XBreakpointType myBreakpointType;

  public XBreakpointTypeGroup(XBreakpointType type) {
    myBreakpointType = type;
  }

  @Override
  public @NotNull String getName() {
    return myBreakpointType.getTitle();
  }

  public XBreakpointType getBreakpointType() {
    return myBreakpointType;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return myBreakpointType.getEnabledIcon();
  }

  @Override
  public int compareTo(XBreakpointGroup o) {
    if (getName().equals(o.getName())) {
      return 0;
    }
    if (o instanceof XBreakpointTypeGroup) {
      if (((XBreakpointTypeGroup)o).myBreakpointType instanceof XLineBreakpointType) {
        if (myBreakpointType instanceof XLineBreakpointType) {
          int res = ((XLineBreakpointType<?>)((XBreakpointTypeGroup)o).myBreakpointType).getPriority() -
                    ((XLineBreakpointType<?>)myBreakpointType).getPriority();
          if (res != 0) {
            return res;
          }
        }
        else {
          // line breakpoints should be on top
          return 1;
        }
      }
      else if (myBreakpointType instanceof XLineBreakpointType) {
        return -1;
      }
      return Long.compare(indexOfType(myBreakpointType), indexOfType(((XBreakpointTypeGroup)o).getBreakpointType()));
    }
    return -o.compareTo(this);
  }

  private static long indexOfType(XBreakpointType type) {
    return XBreakpointUtil.breakpointTypes().indexOf(type).orElse(-1);
  }
}
