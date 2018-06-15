// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.openapi.util.SystemInfo;
import com.jediterm.terminal.model.JediTerminal;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public class TerminalInputBuffer {

  private final JediTerminal myTerminal;
  private int myInputTextStartX = 0;
  private int myInputTextLength = 0;

  public TerminalInputBuffer(@NotNull JediTerminal terminal) {
    myTerminal = terminal;
  }

  public boolean keyPressed(KeyEvent e) {
    if (isDisabled()) {
      return false;
    }
    switch (e.getKeyCode()) {
      case KeyEvent.VK_DELETE:
        if (myInputTextLength > 0 && myTerminal.getX() < myInputTextStartX + myInputTextLength) {
          myInputTextLength--;
          myTerminal.deleteCharacters(1);
        }
        return false;
      case KeyEvent.VK_BACK_SPACE:
        if (myInputTextLength > 0 && myInputTextStartX < myTerminal.getX()) {
          myInputTextLength--;
          myTerminal.backspace();
          myTerminal.deleteCharacters(1);
        }
        return false;
      case KeyEvent.VK_ENTER:
        myTerminal.nextLine();
        myInputTextLength = 0;
        myInputTextStartX = 0;
        return false;
      case KeyEvent.VK_LEFT:
        if (myInputTextLength > 0 && myInputTextStartX < myTerminal.getX()) {
          myTerminal.cursorBackward(1);
        }
        return true;
      case KeyEvent.VK_RIGHT:
        if (myInputTextLength > 0 && myTerminal.getX() < myInputTextStartX + myInputTextLength) {
          myTerminal.cursorForward(1);
        }
        return true;
    }
    return false;
  }

  public void inputStringSent(@NotNull String string) {
    if (isDisabled()) {
      return;
    }
    if (myInputTextLength == 0) {
      myInputTextStartX = myTerminal.getX();
    }
    myInputTextLength += string.length();
    myTerminal.writeCharacters(string);
  }

  private static boolean isDisabled() {
    // WinPty copies characters passed to process's input to its output =>
    // No need to emulate terminal buffer for winpty.
    return SystemInfo.isWindows;
  }
}
