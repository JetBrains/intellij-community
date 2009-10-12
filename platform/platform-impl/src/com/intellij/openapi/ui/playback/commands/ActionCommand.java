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

import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class ActionCommand extends TypeCommand {

  public static String PREFIX = CMD_PREFIX + "action";

  public ActionCommand(String text, int line) {
    super(text, line);
  }

  protected ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String actionName = getText().substring(PREFIX.length()).trim();

    final AnAction action = ActionManager.getInstance().getAction(actionName);
    if (action == null) {
      dumpError(cb, "Unknown action: " + actionName);
      return new ActionCallback.Rejected();
    }


    final Shortcut[] sc = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
    KeyStroke stroke = null;
    for (Shortcut each : sc) {
      if (each instanceof KeyboardShortcut) {
        final KeyboardShortcut ks = (KeyboardShortcut)each;
        final KeyStroke first = ks.getFirstKeyStroke();
        final KeyStroke second = ks.getSecondKeyStroke();
        if (first != null && second == null) {
          stroke = KeyStroke.getKeyStroke(first.getKeyCode(), first.getModifiers(), false);
        }
      }
    }

    if (stroke != null) {
      cb.message("Invoking action via shortcut: " + stroke.toString(), getLine());
      type(robot, stroke);
      return new ActionCallback.Done();
    }

    final InputEvent input = getInputEvent(actionName);

    final ActionCallback result = new ActionCallback();

    robot.delay(Registry.intValue("actionSystem.playback.autodelay"));
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ActionManager.getInstance().tryToExecute(action, input, null, null, false).notifyWhenDone(result);
      }
    });

    return result;
  }

  private InputEvent getInputEvent(String actionName) {
    final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
    KeyStroke keyStroke = null;
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
        keyStroke = ((KeyboardShortcut)each).getFirstKeyStroke();
        if (keyStroke != null) break;
      }
    }

    if (keyStroke != null) {
      return new KeyEvent(JOptionPane.getRootFrame(),
                                             KeyEvent.KEY_PRESSED,
                                             System.currentTimeMillis(),
                                             keyStroke.getModifiers(),
                                             keyStroke.getKeyCode(),
                                             keyStroke.getKeyChar(),
                                             KeyEvent.KEY_LOCATION_STANDARD);
    } else {
      return new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
    }


  }
}