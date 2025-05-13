// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.Shortcut;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class ShortcutPanel<T extends Shortcut> extends JPanel {
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
