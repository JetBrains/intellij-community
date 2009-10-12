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
import java.awt.event.KeyEvent;

public class AlphaNumericTypeCommand extends TypeCommand {

  public AlphaNumericTypeCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String text = getText();
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
    return new ActionCallback.Done();
  }
}