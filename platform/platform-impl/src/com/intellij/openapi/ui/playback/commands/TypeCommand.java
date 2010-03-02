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
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.commands.KeyStokeMap;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class TypeCommand extends AbstractCommand {

  private static KeyStokeMap ourMap = new KeyStokeMap();

  public TypeCommand(String text, int line) {
    super(text, line);
  }

  protected void type(Robot robot, int code, int modfiers) {
    type(robot, KeyStroke.getKeyStroke(code, modfiers));
  }

  protected void type(Robot robot, KeyStroke keyStroke) {
    boolean shift = (keyStroke.getModifiers() & KeyEvent.SHIFT_MASK) > 0;
    boolean alt = (keyStroke.getModifiers() & KeyEvent.ALT_MASK) > 0;
    boolean control = (keyStroke.getModifiers() & KeyEvent.CTRL_MASK) > 0;
    boolean meta = (keyStroke.getModifiers() & KeyEvent.META_MASK) > 0;

    if (shift) {
      robot.keyPress(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyPress(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyPress(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyPress(KeyEvent.VK_META);
    }

    if (keyStroke.getKeyCode() > 0) {
      robot.keyPress(keyStroke.getKeyCode());
      robot.delay(Registry.intValue("actionSystem.playback.autodelay"));
      robot.keyRelease(keyStroke.getKeyCode());
    } else {
      robot.keyPress(keyStroke.getKeyChar());
      robot.delay(Registry.intValue("actionSystem.playback.autodelay"));
      robot.keyRelease(keyStroke.getKeyChar());
    }


    if (shift) {
      robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyRelease(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyRelease(KeyEvent.VK_META);
    }
  }

  protected KeyStroke get(char c) {
    return ourMap.get(c);
  }

  protected KeyStroke getFromShortcut(String sc) {
    return ourMap.get(sc);
  }

  public static boolean containsUnicode(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!ourMap.containsChar(s.charAt(i))) return true;
    }

    return false;
  }
}