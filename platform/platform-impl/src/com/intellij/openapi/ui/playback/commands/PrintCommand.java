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
import com.intellij.openapi.util.ActionCallback;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/17/11
 * Time: 1:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class PrintCommand extends AbstractCommand {

  private String myText;
  
  public PrintCommand(String text, int line) {
    super("", line);
    myText = text;
  }

  @Override
  protected ActionCallback _execute(PlaybackContext context) {
    context.code(myText, getLine());
    return new ActionCallback.Done();
  }
}
