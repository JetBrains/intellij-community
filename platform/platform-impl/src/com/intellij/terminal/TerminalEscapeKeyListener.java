// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Moves focus to editor on Escape key pressed, similarly to {@link com.intellij.openapi.wm.impl.InternalDecorator#processKeyBinding}.
 * Respects ESC+F/ESC+B and other combinations with ESC.
 */
public class TerminalEscapeKeyListener {
  private final JBTerminalPanel myTerminalPanel;
  private final AnAction myTerminalSwitchFocusToEditorAction;
  private boolean myShortcutPressed = false;

  public TerminalEscapeKeyListener(@NotNull JBTerminalPanel terminalPanel) {
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
    if (shouldSwitchFocusToEditor()) {
      Project project = myTerminalPanel.getContextProject();
      if (project != null && !project.isDisposed()) {
        // Repeat logic from com.intellij.openapi.wm.impl.InternalDecorator#processKeyBinding
        ToolWindowManager.getInstance(project).activateEditorComponent();
      }
    }
  }

  private boolean shouldSwitchFocusToEditor() {
    if (myTerminalPanel.getTerminalTextBuffer().isUsingAlternateBuffer()) {
      return false;
    }
    ToolWindow toolWindow = myTerminalPanel.getContextToolWindow();
    if (toolWindow == null) {
      return false;
    }
    if (JBTerminalWidget.isTerminalToolWindow(toolWindow) && !Registry.is("terminal.escape.moves.focus.to.editor")) {
      return false; // For example, vi key bindings configured in terminal
    }
    return true;
  }
}
