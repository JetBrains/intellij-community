// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class StopCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "stop";

  public StopCommand(String text, int line) {
    super(text, line);
  }

  protected Promise<Object> _execute(PlaybackContext context) {
    context.message("Stopped", getLine());
    return Promises.resolvedPromise();
  }

  @Override
  public boolean canGoFurther() {
    return false;
  }
}