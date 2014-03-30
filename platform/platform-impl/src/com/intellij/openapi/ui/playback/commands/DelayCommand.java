/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;

public class DelayCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "delay";

  public DelayCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackContext context) {
    final String s = getText().substring(PREFIX.length()).trim();

    try {
      final Integer delay = Integer.valueOf(s);
      context.getRobot().delay(delay.intValue());
    }
    catch (NumberFormatException e) {
      dumpError(context, "Invalid delay value: " + s);
      return new ActionCallback.Rejected();
    }

    return new ActionCallback.Done();
  }
}