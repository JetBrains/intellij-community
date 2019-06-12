// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.jediterm.terminal.ui.TerminalPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Moves focus to editor on Escape key pressed, similarly to {@link com.intellij.openapi.wm.impl.InternalDecorator#init}.
 * Respects ESC+F/ESC+B and other combinations with ESC.
 */
public class TerminalEscapeKeyListener {
  private final TerminalPanel myTerminalPanel;
  private final AnAction myTerminalSwitchFocusToEditorAction;
  private boolean myShortcutPressed = false;

  public TerminalEscapeKeyListener(@NotNull TerminalPanel terminalPanel) {
    myTerminalPanel = terminalPanel;
    myTerminalSwitchFocusToEditorAction = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor");
  }

  public void handleKeyEvent(@NotNull KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      myShortcutPressed = isMatched(e);
    }
    else if (e.getID() == KeyEvent.KEY_RELEASED) {
      if (myShortcutPressed && isMatched(e)) {
        switchFocusToEditorIfSuitable();
      }
      myShortcutPressed = false;
    }
  }

  private boolean isMatched(@NotNull KeyEvent e) {
    KeyStroke stroke = getKeyStroke();
    return stroke != null && stroke.getKeyCode() == e.getKeyCode() &&
           stroke.getModifiers() == (e.getModifiers() | e.getModifiersEx());
  }

  @Nullable
  private KeyStroke getKeyStroke() {
    if (myTerminalSwitchFocusToEditorAction == null) {
      return null;
    }
    return KeymapUtil.getKeyStroke(myTerminalSwitchFocusToEditorAction.getShortcutSet());
  }

  private void switchFocusToEditorIfSuitable() {
    if (!myTerminalPanel.getTerminalTextBuffer().isUsingAlternateBuffer()) {
      DataContext dataContext = DataManager.getInstance().getDataContext(myTerminalPanel);
      Project project = dataContext.getData(CommonDataKeys.PROJECT);
      if (project != null && !project.isDisposed()) {
        if (isTerminalToolWindow(dataContext.getData(PlatformDataKeys.TOOL_WINDOW)) &&
            !Registry.is("terminal.escape.moves.focus.to.editor")) {
          return; // For example, vi key bindings configured in terminal
        }
        // Repeat logic of InternalDecorator#init from 8cf12b35fe3e44a32622f52a151ed2bf8880faba
        ToolWindowManager.getInstance(project).activateEditorComponent();
      }
    }
  }

  private static boolean isTerminalToolWindow(@Nullable ToolWindow toolWindow) {
    return toolWindow instanceof ToolWindowImpl && "Terminal".equals(((ToolWindowImpl)toolWindow).getId());
  }
}
