/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Represents a node in debugger tree. This interface isn't supposed to be implemented by a plugin.
 *
 * @see XValue
 *
 * @author nik
 */
public interface XValueNode extends Obsolescent {
  /**
   * If value text exceeds this constant it's recommended to truncate it and use {@link #setFullValueEvaluator(XFullValueEvaluator)} method
   * to provide full value
   */
  int MAX_VALUE_LENGTH = 1000;

  /**
   * Setup presentation of the value
   * @param icon icon representing value type (see {@link com.intellij.icons.AllIcons.Debugger})
   * @param type optional type of the value, it is shown in gray color and surrounded by braces
   * @param value string representation of value. It is also used in 'Copy Value' action
   * @param hasChildren {@code false} if the node is a leaf
   */
  void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren);

  /**
   * Setup presentation of the value. This method allows to change separator between name and value and customize the way value text is shown
   * @param icon icon representing value type (see {@link com.intellij.icons.AllIcons.Debugger})
   * @param presentation a new {@link XValuePresentation} instance which determines how the value is show
   * @param hasChildren {@code false} if the node is a leaf
   */
  void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren);

  /**
   * @deprecated use {@link #setPresentation(Icon, XValuePresentation, boolean)}
   */
  @Deprecated
  void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String separator, @NonNls @Nullable String value, boolean hasChildren);

  /**
   * If string representation of the value is too long to show in the tree pass truncated value to {@link #setPresentation(Icon, String, String, boolean)}
   * method and call this method to provide full value.
   * This will add a link to the node and show popup with full value if an user clicks on that link.
   * @param fullValueEvaluator will be used to obtain full text of the value
   * @see #MAX_VALUE_LENGTH
   */
  void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator);
}