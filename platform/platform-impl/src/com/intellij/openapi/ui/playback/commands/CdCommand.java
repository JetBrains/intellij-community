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

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/23/11
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class CdCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "cd";
  private String myDir;

  public CdCommand(String text, int line) {
    super(text, line);
    myDir = text.substring(PREFIX.length()).trim();
  }

  @Override
  protected ActionCallback _execute(PlaybackContext context) {
    File file = context.getPathMacro().resolveFile(myDir, context.getBaseDir());
    if (!file.exists()) {
      context.message("Cannot cd, directory doesn't exist: " + file.getAbsoluteFile(), getLine());
      return new ActionCallback.Rejected();
    }

    try {
      context.setBaseDir(file.getCanonicalFile());
    }
    catch (IOException e) {
      context.setBaseDir(file);
    }
    
    context.message("{base.dir} set to " + context.getBaseDir().getAbsolutePath(), getLine());
    return new ActionCallback.Done();
  }
}
