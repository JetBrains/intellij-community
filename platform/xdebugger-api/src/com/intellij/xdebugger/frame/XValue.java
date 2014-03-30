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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value in debugger tree.
 * Override {@link XValueContainer#computeChildren} if value has a properties which should be shown as child nodes
 *
 * @author nik
 */
public abstract class XValue extends XValueContainer {
  /**
   * Start computing presentation of the value in the debugger tree and call {@link XValueNode#setPresentation(javax.swing.Icon, String, String, boolean)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   * @param node node
   * @param place where the node will be shown.
   */
  public abstract void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place);

  /**
   * @return expression which evaluates to the current value
   */
  @Nullable
  public String getEvaluationExpression() {
    return null;
  }

  /**
   * @return {@link com.intellij.xdebugger.frame.XValueModifier} instance which can be used to modify the value
   */
  @Nullable
  public XValueModifier getModifier() {
    return null;
  }

  /**
   * Start computing source position of the value and call {@link XNavigatable#setSourcePosition(com.intellij.xdebugger.XSourcePosition)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   * @param navigatable navigatable
   */
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(null);
  }

  /**
   * Return {@code true} from this method and override {@link #computeSourcePosition(XNavigatable)} if navigation to the source
   * is supported for the value
   * @return {@code true} if navigation to the value's source is supported
   */
  public boolean canNavigateToSource() {
    // should be false, but cannot be due to compatibility reasons
    return true;
  }

  /**
   * Return {@code true} from this method and override {@link #computeTypeSourcePosition(XNavigatable)} if navigation to the value's type
   * is supported for the value
   * @return {@code true} if navigation to the value's type is supported
   */
  public boolean canNavigateToTypeSource() {
    return false;
  }

  /**
   * Start computing source position of the value's type and call {@link XNavigatable#setSourcePosition(com.intellij.xdebugger.XSourcePosition)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   */
  public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(null);
  }
}