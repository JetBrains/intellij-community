// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.Shortcut;

import java.awt.LayoutManager;
import javax.swing.JPanel;

class ShortcutPanel<T extends Shortcut> extends JPanel {
  private T myShortcut;

  ShortcutPanel(LayoutManager layout) {
    super(layout);
  }

  T getShortcut() {
    return myShortcut;
  }

  void setShortcut(T shortcut) {
    T old = myShortcut;
    myShortcut = shortcut;
    firePropertyChange("shortcut", old, shortcut);
  }
}
