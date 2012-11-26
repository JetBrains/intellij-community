/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerUIConstants {
  public static final String COLLECTING_DATA_MESSAGE = XDebuggerBundle.message("xdebugger.building.tree.node.message");
  public static final String EVALUATING_EXPRESSION_MESSAGE = XDebuggerBundle.message("xdebugger.evaluating.expression.node.message");
  public static final String MODIFYING_VALUE_MESSAGE = XDebuggerBundle.message("xdebugger.modifiyng.value.node.message");

  public static final Icon ERROR_MESSAGE_ICON = AllIcons.General.Error;
  public static final Icon INFORMATION_MESSAGE_ICON = AllIcons.General.Information;

  public static final SimpleTextAttributes COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES = get(new JBColor(Color.lightGray, Color.lightGray));
  public static final SimpleTextAttributes EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES = get(new JBColor(Color.lightGray, Color.lightGray));
  public static final SimpleTextAttributes MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES = get(JBColor.blue);
  public static final SimpleTextAttributes CHANGED_VALUE_ATTRIBUTES = get(JBColor.blue);
  public static final SimpleTextAttributes EXCEPTION_ATTRIBUTES = get(JBColor.red);
  public static final SimpleTextAttributes VALUE_NAME_ATTRIBUTES = get(new JBColor(new Color(128, 0, 0), DarculaColors.RED.brighter()));
  public static final SimpleTextAttributes ERROR_MESSAGE_ATTRIBUTES = get(JBColor.red);
  @NonNls public static final String EQ_TEXT = " = ";

  public static final SimpleTextAttributes TYPE_ATTRIBUTES = SimpleTextAttributes.GRAY_ATTRIBUTES;
  public static final String LAYOUT_VIEW_BREAKPOINT_CONDITION = "breakpoint";

  private static SimpleTextAttributes get(JBColor c) {
    return new SimpleTextAttributes(Font.PLAIN, c);
  }

  private XDebuggerUIConstants() {
  }
}
