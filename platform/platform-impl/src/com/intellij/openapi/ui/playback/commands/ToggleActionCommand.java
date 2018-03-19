// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.awt.event.InputEvent;

public class ToggleActionCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "toggle";

  public static final String ON = "on";
  public static final String OFF = "off";

  public ToggleActionCommand(String text, int line) {
    super(text, line, true);
  }

  @Override
  protected Promise<Object> _execute(PlaybackContext context) {
    String[] args = getText().substring(PREFIX.length()).trim().split(" ");
    String syntaxText = "Syntax error, expected: " + PREFIX + " " + ON + "|" + OFF + " actionName";
    if (args.length != 2) {
      context.error(syntaxText, getLine());
      return Promises.rejectedPromise(new RuntimeException(syntaxText));
    }

    final boolean on;
    if (ON.equalsIgnoreCase(args[0])) {
      on = true;
    } else if (OFF.equalsIgnoreCase(args[0])) {
      on = false;
    } else {
      context.error(syntaxText, getLine());
      return Promises.rejectedPromise(new RuntimeException(syntaxText));
    }

    String actionId = args[1];
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      context.error("Unknown action id=" + actionId, getLine());
      return Promises.rejectedPromise(new RuntimeException("Unknown action id=" + actionId));
    }

    if (!(action instanceof ToggleAction)) {
      String text = "Action is not a toggle action id=" + actionId;
      context.error(text, getLine());
      return Promises.rejectedPromise(text);
    }

    final InputEvent inputEvent = ActionCommand.getInputEvent(actionId);
    final ActionCallback result = new ActionCallback();

    context.getRobot().delay(Registry.intValue("actionSystem.playback.delay"));

    IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    fm.doWhenFocusSettlesDown(() -> {
      final Presentation presentation = action.getTemplatePresentation().clone();
      AnActionEvent event =
          new AnActionEvent(inputEvent, DataManager.getInstance()
              .getDataContext(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()), ActionPlaces.UNKNOWN,
                            presentation, ActionManager.getInstance(), 0);

      ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, event, false);

      Boolean state = (Boolean)event.getPresentation().getClientProperty(ToggleAction.SELECTED_PROPERTY);
      if (state.booleanValue() != on) {
        ActionManager.getInstance().tryToExecute(action, inputEvent, null, ActionPlaces.UNKNOWN, true).doWhenProcessed(result.createSetDoneRunnable());
      }
      else {
        result.setDone();
      }
    });


    return Promises.toPromise(result);
  }
}
