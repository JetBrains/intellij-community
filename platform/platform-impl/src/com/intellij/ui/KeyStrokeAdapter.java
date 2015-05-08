/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.Patches;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Method;

/**
 * @author Sergey.Malenkov
 */
public class KeyStrokeAdapter implements KeyListener {
  @Override
  public void keyTyped(KeyEvent event) {
    keyTyped(event, getKeyStroke(event, false));
  }

  protected boolean keyTyped(KeyStroke stroke) {
    return false;
  }

  private void keyTyped(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyTyped(stroke)) {
      event.consume();
    }
  }

  @Override
  public void keyPressed(KeyEvent event) {
    keyPressed(event, getKeyStroke(event, true));
    keyPressed(event, getKeyStroke(event, false));
  }

  protected boolean keyPressed(KeyStroke stroke) {
    return false;
  }

  private void keyPressed(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyPressed(stroke)) {
      event.consume();
    }
  }

  @Override
  public void keyReleased(KeyEvent event) {
    keyReleased(event, getKeyStroke(event, true));
    keyReleased(event, getKeyStroke(event, false));
  }

  protected boolean keyReleased(KeyStroke stroke) {
    return false;
  }

  private void keyReleased(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyReleased(stroke)) {
      event.consume();
    }
  }

  /**
   * @param event the specified key event to process
   * @return a key stroke or {@code null} if it is not applicable
   * @see KeyStroke#getKeyStrokeForEvent(KeyEvent)
   */
  public static KeyStroke getDefaultKeyStroke(KeyEvent event) {
    if (event == null || event.isConsumed()) return null;
    // On Windows and Mac it is preferable to use normal key code here
    boolean extendedKeyCodeFirst = !SystemInfo.isWindows && !SystemInfo.isMac && event.getModifiers() == 0;
    KeyStroke stroke = getKeyStroke(event, extendedKeyCodeFirst);
    return stroke != null ? stroke : getKeyStroke(event, !extendedKeyCodeFirst);
  }

  /**
   * @param event    the specified key event to process
   * @param extended {@code true} if extended key code should be used
   * @return a key stroke or {@code null} if it is not applicable
   * @see JComponent#processKeyBindings(KeyEvent, boolean)
   */
  public static KeyStroke getKeyStroke(KeyEvent event, boolean extended) {
    if (event != null && !event.isConsumed()) {
      int id = event.getID();
      if (id == KeyEvent.KEY_TYPED) {
        return extended ? null : getKeyStroke(event.getKeyChar());
      }
      boolean released = id == KeyEvent.KEY_RELEASED;
      if (released || id == KeyEvent.KEY_PRESSED) {
        int code = event.getKeyCode();
        if (extended) {
          if (Registry.is("actionSystem.extendedKeyCode.disabled")) {
            return null;
          }
          code = getExtendedKeyCode(event);
          if (code == event.getKeyCode()) {
            return null;
          }
        }
        return getKeyStroke(code, event.getModifiers(), released);
      }
    }
    return null;
  }

  /**
   * @param ch the specified key character
   * @return a key stroke or {@code null} if {@code ch} is undefined
   */
  private static KeyStroke getKeyStroke(char ch) {
    return KeyEvent.CHAR_UNDEFINED == ch ? null : KeyStroke.getKeyStroke(ch);
  }

  /**
   * @param code      the numeric code for a keyboard key
   * @param modifiers the modifier mask from the event
   * @param released  {@code true} if the key stroke should represent a key release
   * @return a key stroke or {@code null} if {@code code} is undefined
   */
  private static KeyStroke getKeyStroke(int code, int modifiers, boolean released) {
    return KeyEvent.VK_UNDEFINED == code ? null : KeyStroke.getKeyStroke(code, modifiers, released);
  }

  // TODO: HACK because of Java7 required:
  // replace later with event.getExtendedKeyCode()
  private static int getExtendedKeyCode(KeyEvent event) {
    //noinspection ConstantConditions
    assert Patches.USE_REFLECTION_TO_ACCESS_JDK7;
    try {
      Method method = KeyEvent.class.getMethod("getExtendedKeyCode");
      if (!method.isAccessible()) {
        method.setAccessible(true);
      }
      return (Integer)method.invoke(event);
    }
    catch (Exception exception) {
      return event.getKeyCode();
    }
  }
}
