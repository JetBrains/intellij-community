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
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.event.InputEvent;

/**
 * @author max
 */
public class MacOSDefaultKeymap extends DefaultKeymapImpl {
  protected String[] getParentActionIds(KeyStroke firstKeyStroke) {
    return super.getParentActionIds(convertKeyStroke(firstKeyStroke));
  }

  protected String[] getParentActionIds(MouseShortcut shortcut) {
    return super.getParentActionIds(convertMouseShortcut(shortcut));
  }

  protected Shortcut[] getParentShortcuts(String actionId) {
    Shortcut[] parentShortcuts = super.getParentShortcuts(actionId);
    Shortcut[] macShortcuts = new Shortcut[parentShortcuts.length];
    for (int i = 0; i < parentShortcuts.length; i++) {
      macShortcuts[i] = convertShortcutFromParent(parentShortcuts[i]);
    }
    return macShortcuts;
  }

  public static Shortcut convertShortcutFromParent(Shortcut parentShortcut) {
    if (parentShortcut instanceof MouseShortcut) {
      return convertMouseShortcut((MouseShortcut)parentShortcut);
    }

    KeyboardShortcut key = (KeyboardShortcut)parentShortcut;
    return new KeyboardShortcut(convertKeyStroke(key.getFirstKeyStroke()),
                                convertKeyStroke(key.getSecondKeyStroke()));
  }

  private static KeyStroke convertKeyStroke(KeyStroke parentKeyStroke) {
    if (parentKeyStroke == null) return null;
    return KeyStroke.getKeyStroke(parentKeyStroke.getKeyCode(),
                                  mapModifiers(parentKeyStroke.getModifiers()),
                                  parentKeyStroke.isOnKeyRelease());
  }

  private static MouseShortcut convertMouseShortcut(MouseShortcut macShortcut) {
    return new MouseShortcut(macShortcut.getButton(),
                             mapModifiers(macShortcut.getModifiers()),
                             macShortcut.getClickCount());
  }

  @JdkConstants.InputEventMask
  private static int mapModifiers(@JdkConstants.InputEventMask int modifiers) {
    boolean meta = false;

    if ((modifiers & InputEvent.META_MASK) != 0) {
      modifiers &= ~InputEvent.META_MASK;
      meta = true;
    }

    boolean metaDown = false;
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      modifiers &= ~InputEvent.META_DOWN_MASK;
      metaDown = true;
    }

    boolean control = false;
    if ((modifiers & InputEvent.CTRL_MASK) != 0) {
      modifiers &= ~InputEvent.CTRL_MASK;
      control = true;
    }

    boolean controlDown = false;
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      modifiers &= ~InputEvent.CTRL_DOWN_MASK;
      controlDown = true;
    }

    if (meta) {
      modifiers |= InputEvent.CTRL_MASK;
    }

    if (metaDown) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }

    if (control) {
      modifiers |= InputEvent.META_MASK;
    }

    if (controlDown) {
      modifiers |= InputEvent.META_DOWN_MASK;
    }

    return modifiers;
  }
}
