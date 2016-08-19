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
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TimedOutCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class ActionCommand extends TypeCommand {

  public static String PREFIX = CMD_PREFIX + "action";

  public ActionCommand(String text, int line) {
    super(text, line);
  }

  protected ActionCallback _execute(final PlaybackContext context) {
    final String actionName = getText().substring(PREFIX.length()).trim();

    final ActionManager am = ActionManager.getInstance();
    final AnAction targetAction = am.getAction(actionName);
    if (targetAction == null) {
      dumpError(context, "Unknown action: " + actionName);
      return ActionCallback.REJECTED;
    }


    if (!context.isUseDirectActionCall()) {
      final Shortcut[] sc = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
      KeyStroke stroke = null;
      for (Shortcut each : sc) {
        if (each instanceof KeyboardShortcut) {
          final KeyboardShortcut ks = (KeyboardShortcut)each;
          final KeyStroke first = ks.getFirstKeyStroke();
          final KeyStroke second = ks.getSecondKeyStroke();
          if (second == null) {
            stroke = KeyStroke.getKeyStroke(first.getKeyCode(), first.getModifiers(), false);
            break;
          }
        }
      }

      if (stroke != null) {
        final ActionCallback result = new TimedOutCallback(Registry.intValue("actionSystem.commandProcessingTimeout"), "Timed out calling action id=" + actionName, new Throwable(), true) {
          @Override
          protected void dumpError() {
            context.error(getMessage(), getLine());
          }
        };
        context.message("Invoking action via shortcut: " + stroke.toString(), getLine());

        final KeyStroke finalStroke = stroke;

        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          final Ref<AnActionListener> listener = new Ref<>();
          listener.set(new AnActionListener.Adapter() {

            @Override
            public void beforeActionPerformed(final AnAction action, DataContext dataContext, AnActionEvent event) {
              SwingUtilities.invokeLater(() -> {
                if (context.isDisposed()) {
                  am.removeAnActionListener(listener.get());
                  return;
                }

                if (targetAction.equals(action)) {
                  context.message("Performed action: " + actionName, context.getCurrentLine());
                  am.removeAnActionListener(listener.get());
                  result.setDone();
                }
              });
            }
          });
          am.addAnActionListener(listener.get());

          context.runPooledThread(() -> type(context.getRobot(), finalStroke));
        });

        return result;
      }
    }

    final InputEvent input = getInputEvent(actionName);

    final ActionCallback result = new ActionCallback();

    context.getRobot().delay(Registry.intValue("actionSystem.playback.delay"));
    SwingUtilities.invokeLater(
      () -> am.tryToExecute(targetAction, input, null, null, false).doWhenProcessed(result.createSetDoneRunnable()));

    return result;
  }

  public static InputEvent getInputEvent(String actionName) {
    final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
    KeyStroke keyStroke = null;
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
        keyStroke = ((KeyboardShortcut)each).getFirstKeyStroke();
        break;
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