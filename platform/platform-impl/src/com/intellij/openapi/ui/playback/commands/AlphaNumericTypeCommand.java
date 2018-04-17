// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class AlphaNumericTypeCommand extends TypeCommand {

  public AlphaNumericTypeCommand(String text, int line) {
    super(text, line, true);
  }

  public Promise<Object> _execute(PlaybackContext context) {
    return type(context, getText());
  }

  protected Promise<Object> type(final PlaybackContext context, final String text) {
    final ActionCallback result = new ActionCallback();

    inWriteSafeContext(() -> {
      TypingTarget typingTarget = findTarget(context);
      if (typingTarget != null) {
        typingTarget.type(text).doWhenDone(result.createSetDoneRunnable()).doWhenRejected(() -> typeByRobot(context.getRobot(), text).notify(result));
      } else {
        typeByRobot(context.getRobot(), text).notify(result);
      }
    });

    return Promises.toPromise(result);
  }

  private ActionCallback typeByRobot(final Robot robot, final String text) {
    final ActionCallback result = new ActionCallback();

    Runnable typeRunnable = () -> {
      for (int i = 0; i < text.length(); i++) {
        final char each = text.charAt(i);
        if ('\\' == each && i + 1 < text.length()) {
          final char next = text.charAt(i + 1);
          boolean processed = true;
          switch (next) {
            case 'n':
              type(robot, KeyEvent.VK_ENTER, 0);
              break;
            case 't':
              type(robot, KeyEvent.VK_TAB, 0);
              break;
            case 'r':
              type(robot, KeyEvent.VK_ENTER, 0);
              break;
            default:
              processed = false;
          }

          if (processed) {
            i++;
            continue;
          }
        }
        type(robot, get(each));
      }

      result.setDone();
    };

    if (SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(typeRunnable);
    } else {
      typeRunnable.run();
    }

    return result;
  }

  @Nullable
  public static TypingTarget findTarget(PlaybackContext context) {
    if (!context.isUseTypingTargets()) return null;

    Component each = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    while (each != null) {
      if (each instanceof TypingTarget) {
        return (TypingTarget)each;
      }

      each = each.getParent();
    }

    return null;
  }


}