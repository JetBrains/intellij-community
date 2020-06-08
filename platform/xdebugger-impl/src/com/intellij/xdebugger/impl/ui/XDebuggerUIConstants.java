// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public final class XDebuggerUIConstants {
  /**
   * @deprecated Use {@link #getCollectingDataMessage()} instead
   */
  @Deprecated
  public static final String COLLECTING_DATA_MESSAGE = "Collecting data…";

  public static final Icon ERROR_MESSAGE_ICON = AllIcons.General.Error;
  public static final Icon INFORMATION_MESSAGE_ICON = AllIcons.General.Information;

  public static final SimpleTextAttributes COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.collectingDataForeground", new JBColor(Color.lightGray, Color.lightGray)));
  public static final SimpleTextAttributes EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.evaluatingExpressionForeground", new JBColor(Color.lightGray, Color.lightGray)));
  public static final SimpleTextAttributes MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.modifyingValueForeground", JBColor.blue));
  public static final SimpleTextAttributes CHANGED_VALUE_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.changedValueForeground", JBColor.blue));
  public static final SimpleTextAttributes EXCEPTION_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.exceptionForeground", JBColor.red));
  public static final SimpleTextAttributes VALUE_NAME_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.valueForeground", new JBColor(new Color(128, 0, 0), DarculaColors.RED.brighter())));
  public static final SimpleTextAttributes ERROR_MESSAGE_ATTRIBUTES =
    get(JBColor.namedColor("Debugger.Variables.errorMessageForeground", JBColor.red));
  @NonNls public static final String EQ_TEXT = " = ";

  public static final SimpleTextAttributes TYPE_ATTRIBUTES = SimpleTextAttributes.GRAY_ATTRIBUTES;
  public static final String LAYOUT_VIEW_BREAKPOINT_CONDITION = "breakpoint";

  private static SimpleTextAttributes get(JBColor c) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, c);
  }

  private XDebuggerUIConstants() {
  }

  public static String getCollectingDataMessage() {
    return XDebuggerBundle.message("xdebugger.building.tree.node.message");
  }

  public static String getEvaluatingExpressionMessage() {
    return XDebuggerBundle.message("xdebugger.evaluating.expression.node.message");
  }

  public static String getModifyingValueMessage() {
    return XDebuggerBundle.message("xdebugger.modifiyng.value.node.message");
  }
}
