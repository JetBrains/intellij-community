// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TimedOutCallback;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

public class ActionCommand extends TypeCommand {
  public static final String PREFIX = CMD_PREFIX + "action";

  public ActionCommand(String text, int line) {
    super(text, line, true);
  }

  protected Promise<Object> _execute(final PlaybackContext context) {
    final String actionName = getText().substring(PREFIX.length()).trim();

    final ActionManager am = ActionManager.getInstance();
    final AnAction targetAction = am.getAction(actionName);
    if (targetAction == null) {
      dumpError(context, "Unknown action: " + actionName);
      return Promises.rejectedPromise();
    }

    if (!context.isUseDirectActionCall()) {
      final Shortcut[] sc = getActiveKeymapShortcuts(actionName).getShortcuts();
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

        inWriteSafeContext(() -> {
          final Ref<AnActionListener> listener = new Ref<>();
          listener.set(new AnActionListener.Adapter() {

            @Override
            public void beforeActionPerformed(final AnAction action, DataContext dataContext, AnActionEvent event) {
              ApplicationManager.getApplication().invokeLater(() -> {
                if (context.isDisposed()) {
                  am.removeAnActionListener(listener.get());
                  return;
                }

                if (targetAction.equals(action)) {
                  context.message("Performed action: " + actionName, context.getCurrentLine());
                  am.removeAnActionListener(listener.get());
                  result.setDone();
                }
              }, ModalityState.any());
            }
          });
          am.addAnActionListener(listener.get());

          context.runPooledThread(() -> type(context.getRobot(), finalStroke));
        });

        return Promises.toPromise(result);
      }
    }

    final InputEvent input = getInputEvent(actionName);

    final ActionCallback result = new ActionCallback();

    context.getRobot().delay(Registry.intValue("actionSystem.playback.delay"));
    ApplicationManager.getApplication().invokeLater(
      () -> am.tryToExecute(targetAction, input, null, null, false).doWhenProcessed(result.createSetDoneRunnable()), ModalityState.any());

    return Promises.toPromise(result);
  }

  public static InputEvent getInputEvent(String actionName) {
    final Shortcut[] shortcuts = getActiveKeymapShortcuts(actionName).getShortcuts();
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