// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author gregsh
 */
public abstract class HeldDownKeyListener {

  private final KeyStroke myKeyStroke;

  public HeldDownKeyListener() {
    this(KeyStroke.getKeyStroke("shift pressed SHIFT"));
  }

  public HeldDownKeyListener(KeyStroke keyStroke) {
    myKeyStroke = keyStroke;
  }

  public void installOn(JComponent component) {
    registerAction(component, "heldDownKey:" + myKeyStroke.toString(), myKeyStroke, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        heldKeyTriggered((JComponent)e.getSource(), true);
      }
    });

    KeyStroke keyStroke2 = KeyStroke.getKeyStroke(myKeyStroke.getKeyCode(), 0, true);
    registerAction(component, "heldDownKey:" + keyStroke2.toString(), keyStroke2, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        heldKeyTriggered((JComponent)e.getSource(), false);
      }
    });
  }

  protected abstract void heldKeyTriggered(JComponent component, boolean pressed);

  private static void registerAction(JComponent component, @NonNls String name, KeyStroke keyStroke, AbstractAction action) {
    component.getInputMap().put(keyStroke, name);
    component.getActionMap().put(name, action);
  }
}
