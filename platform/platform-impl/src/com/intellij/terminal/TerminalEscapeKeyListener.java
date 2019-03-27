// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jediterm.terminal.ui.TerminalPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

/**
 * Moves focus to editor on Escape key pressed, similarly to {@link com.intellij.openapi.wm.impl.InternalDecorator#init}.
 * Respects ESC+F/ESC+B and other combinations with ESC.
 */
public class TerminalEscapeKeyListener {
  private final TerminalPanel myTerminalPanel;
  private boolean myEscapePressed = false;

  public TerminalEscapeKeyListener(@NotNull TerminalPanel terminalPanel) {
    myTerminalPanel = terminalPanel;
  }

  public void handleKeyEvent(@NotNull KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      myEscapePressed = e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0;
    }
    else if (e.getID() == KeyEvent.KEY_RELEASED) {
      if (myEscapePressed && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
        performIdeEscapeAction();
      }
      myEscapePressed = false;
    }
  }

  private void performIdeEscapeAction() {
    if (!myTerminalPanel.getTerminalTextBuffer().isUsingAlternateBuffer()) {
      Project project = DataManager.getInstance().getDataContext(myTerminalPanel).getData(CommonDataKeys.PROJECT);
      if (project != null && !project.isDisposed()) {
        // Repeat logic of InternalDecorator#init from 8cf12b35fe3e44a32622f52a151ed2bf8880faba
        ToolWindowManager.getInstance(project).activateEditorComponent();
      }
    }
  }
}
