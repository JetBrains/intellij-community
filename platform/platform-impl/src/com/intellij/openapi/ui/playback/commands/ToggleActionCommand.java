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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/23/11
 * Time: 2:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class ToggleActionCommand extends AbstractCommand {
  
  public static final String PREFIX = CMD_PREFIX + "toggle";

  public static final String ON = "on";
  public static final String OFF = "off";
  
  public ToggleActionCommand(String text, int line) {
    super(text, line, true);
  }

  @Override
  protected ActionCallback _execute(PlaybackContext context) {
    String[] args = getText().substring(PREFIX.length()).trim().split(" ");
    String syntaxText = "Syntax error, expected: " + PREFIX + " " + ON + "|" + OFF + " actionName";
    if (args.length != 2) {
      context.error(syntaxText, getLine());
      return ActionCallback.REJECTED;
    }
    
    final boolean on;
    if (ON.equalsIgnoreCase(args[0])) {
      on = true;
    } else if (OFF.equalsIgnoreCase(args[0])) {
      on = false;
    } else {
      context.error(syntaxText, getLine());
      return ActionCallback.REJECTED;
    }
    
    String actionId = args[1];
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      context.error("Unknown action id=" + actionId, getLine());
      return ActionCallback.REJECTED;
    }

    if (!(action instanceof ToggleAction)) {
      context.error("Action is not a toggle action id=" + actionId, getLine());
      return ActionCallback.REJECTED;
    }

    final InputEvent inputEvent = ActionCommand.getInputEvent(actionId);
    final ActionCallback result = new ActionCallback();

    context.getRobot().delay(Registry.intValue("actionSystem.playback.delay"));

    IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    fm.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        final Presentation presentation = (Presentation)action.getTemplatePresentation().clone();
        AnActionEvent event =
            new AnActionEvent(inputEvent, DataManager.getInstance()
                .getDataContext(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()), ActionPlaces.UNKNOWN,
                              presentation, ActionManager.getInstance(), 0);

        ActionUtil.performDumbAwareUpdate(action, event, false);

        Boolean state = (Boolean)event.getPresentation().getClientProperty(ToggleAction.SELECTED_PROPERTY);
        if (state.booleanValue() != on) {
          ActionManager.getInstance().tryToExecute(action, inputEvent, null, ActionPlaces.UNKNOWN, true).doWhenProcessed(result.createSetDoneRunnable());
        }
        else {
          result.setDone();
        }
      }
    });


    return result;
  }
}
