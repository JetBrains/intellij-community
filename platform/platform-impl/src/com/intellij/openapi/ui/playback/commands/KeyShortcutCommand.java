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

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class KeyShortcutCommand extends TypeCommand {

  public static String PREFIX = CMD_PREFIX + "[";
  public static String POSTFIX = CMD_PREFIX + "]";

  public KeyShortcutCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String one = getText().substring(PREFIX.length());
    if (!one.endsWith("]")) {
      dumpError(cb, "Expected " + "]");
      return new ActionCallback.Rejected();
    }

    type(robot, getFromShortcut(one.substring(0, one.length() - 1).trim()));

    return new ActionCallback.Done();
  }
}