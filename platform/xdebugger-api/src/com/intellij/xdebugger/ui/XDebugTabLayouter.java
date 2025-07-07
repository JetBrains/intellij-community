// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Allows to customize xdebug layout for 'Debug' tool window.
 *
 * @author Sergey Simonchik
 * @see com.intellij.xdebugger.XDebugProcess#createTabLayouter()
 */
public class XDebugTabLayouter {

  /**
   * Registers tab for the given {@code console}.
   *
   * @param console {@code ExecutionConsole} instance
   * @param ui {@code RunnerLayoutUi} instance
   * @return registered {@code Content} instance
   */
  public @NotNull Content registerConsoleContent(@NotNull RunnerLayoutUi ui, @NotNull ExecutionConsole console) {
    JComponent component = console.getComponent();
    // remove the left border from console view
    UIUtil.removeScrollBorder(component);
    Content content = ui.createContent(DebuggerContentInfo.CONSOLE_CONTENT, component,
                                       XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                       null,
                                       console.getPreferredFocusableComponent());
    content.setCloseable(false);
    ui.addContent(content, 1, PlaceInGrid.bottom, false);
    return content;
  }

  /**
   * Registers additional tabs for 'Debug' tool window.
   * @param ui {@code RunnerLayoutUi} instance
   */
  public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
  }

}
