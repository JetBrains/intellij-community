/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

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

  private static void registerAction(JComponent component, String name, KeyStroke keyStroke, AbstractAction action) {
    component.getInputMap().put(keyStroke, name);
    component.getActionMap().put(name, action);
  }
}
