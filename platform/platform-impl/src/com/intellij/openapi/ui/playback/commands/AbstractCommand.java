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

import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;

import java.awt.*;

public abstract class AbstractCommand implements PlaybackCommand {

  public static String CMD_PREFIX = "%";

  private final String myText;
  private final int myLine;

  public AbstractCommand(String text, int line) {
    myText = text != null ? text : null;
    myLine = line;
  }

  public String getText() {
    return myText;
  }

  public int getLine() {
    return myLine;
  }

  public boolean canGoFurther() {
    return true;
  }

  public final ActionCallback execute(PlaybackRunner.StatusCallback cb, Robot robot, boolean useDirectActionCall) {
    try {
      dumpCommand(cb);
      return _execute(cb, robot, useDirectActionCall);
    }
    catch (Exception e) {
      cb.error(e.getMessage(), getLine());
      return new ActionCallback.Rejected();
    }
  }

  protected abstract ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot, boolean directActionCall);

  public void dumpCommand(final PlaybackRunner.StatusCallback cb) {
    cb.message(getText(), getLine());
  }

  public void dumpError(final PlaybackRunner.StatusCallback cb, final String text) {
    cb.error(text, getLine());
  }
}
