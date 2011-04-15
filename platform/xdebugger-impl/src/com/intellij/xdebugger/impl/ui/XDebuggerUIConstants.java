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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.util.IconLoader;
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

  public static final Icon ERROR_MESSAGE_ICON = IconLoader.getIcon("/debugger/db_error.png");
  public static final Icon INFORMATION_MESSAGE_ICON = IconLoader.getIcon("/compiler/information.png");

  public static final SimpleTextAttributes COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  public static final SimpleTextAttributes EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  public static final SimpleTextAttributes MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.blue);
  public static final SimpleTextAttributes CHANGED_VALUE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.blue);
  public static final SimpleTextAttributes EXCEPTION_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.red);
  public static final SimpleTextAttributes VALUE_NAME_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, new Color(128, 0, 0));
  public static final SimpleTextAttributes ERROR_MESSAGE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.red);
  @NonNls public static final String EQ_TEXT = " = ";

  public static final Icon DEBUG_AGAIN_ICON = IconLoader.getIcon("/actions/startDebugger.png");
  public static final Icon FRAMES_TAB_ICON = IconLoader.getIcon("/debugger/frame.png");
  public static final Icon THREADS_TAB_ICON = IconLoader.getIcon("/debugger/threads.png");
  public static final Icon VARIABLES_TAB_ICON = IconLoader.getIcon("/debugger/value.png");
  public static final Icon WATCHES_TAB_ICON = IconLoader.getIcon("/debugger/watches.png");
  public static final Icon CONSOLE_TAB_ICON = IconLoader.getIcon("/debugger/console.png");
  public static final SimpleTextAttributes TYPE_ATTRIBUTES = SimpleTextAttributes.GRAY_ATTRIBUTES;
  public static final String LAYOUT_VIEW_BREAKPOINT_CONDITION = "breakpoint";

  private XDebuggerUIConstants() {
  }
}
