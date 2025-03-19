// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a frame of the execution stack.
 * The selected frame is shown in the 'Variables' panel of the 'Debug' tool window.
 * <p>
 * When implementing a debugger, override {@link XValueContainer#computeChildren}
 * to show local variables, parameters and fields available in the frame.
 */
public abstract class XStackFrame extends XValueContainer {

  /**
   * If the stack frame is not changed after stepping in the debugger,
   * the expanded nodes and the selection will be restored in the 'Variables' tree.
   * A stack frame is assumed to be unchanged
   * if this method returns equal non-null values before and after stepping.
   *
   * @return an object which will be used to determine if the stack frame changed after stepping
   */
  public @Nullable Object getEqualityObject() {
    return null;
  }

  /**
   * Implement this method to support evaluation in the debugger, in particular:
   * <ul>
   *   <li>conditional breakpoints
   *   <li>logging a message on reaching a breakpoint
   *   <li>the "Evaluate" action
   *   <li>watches
   * </ul>
   */
  public @Nullable XDebuggerEvaluator getEvaluator() {
    return null;
  }

  /**
   * @return the current executing point in the stack frame
   */
  public @Nullable XSourcePosition getSourcePosition() {
    return null;
  }

  /**
   * Customize the presentation of the stack frame in the frame list.
   */
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    XSourcePosition position = getSourcePosition();
    if (position != null) {
      component.append(position.getFile().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append(":" + (position.getLine() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.setIcon(AllIcons.Debugger.Frame);
    }
    else {
      component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}