/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.ui.playback.StageInfo;
import com.intellij.openapi.util.ActionCallback;

public class PushStage extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "startTest";
  
  public PushStage(String text, int line) {
    super(text, line);
  }

  @Override
  protected ActionCallback _execute(PlaybackContext context) {
    String name = getText().substring(PREFIX.length()).trim();
    context.test("Test started: " + name, getLine());
    context.pushStage(new StageInfo(name));
    return new ActionCallback.Done();
  }

  @Override
  protected boolean isToDumpCommand() {
    return false;
  }
}
