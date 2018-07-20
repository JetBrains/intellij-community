/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * Represents a frame of execution stack. The selected frame is shown in 'Variables' panel of 'Debug' tool window.
 * Override {@link XValueContainer#computeChildren} to show local variable, parameters, fields available in the frame
 *
 * @author nik
 */
public abstract class XStackFrame extends XValueContainer {

  /**
   * If stack frame is not changed after step expanded nodes and selection will be restored in 'Variables' tree. A stack frame is assumed
   * unchanged if this method return equal non-null values before and after step
   * @return an object which will be used to determine if stack frame changed after step
   */
  @Nullable
  public Object getEqualityObject() {
    return null;
  }

  /**
   * Implement to support evaluation in debugger (conditional breakpoints, logging message on breakpoint, "Evaluate" action, watches)
   * @return evaluator instance
   */
  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    return null;
  }

  /**
   * @return source position corresponding to stack frame
   */
  @Nullable 
  public XSourcePosition getSourcePosition() {
    return null;
  }

  /**
   * Update info about stack frame
   */
  public void refresh() {
  }

  /**
   * Customize presentation of the stack frame in frames list
   * @param component component
   */
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    XSourcePosition position = getSourcePosition();
    if (position != null) {
      component.append(position.getFile().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append(":" + (position.getLine() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.setIcon(AllIcons.Debugger.StackFrame);
    }
    else {
      component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}