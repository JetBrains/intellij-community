// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class DelayCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "delay";

  public DelayCommand(String text, int line) {
    super(text, line);
  }

  public Promise<Object> _execute(PlaybackContext context) {
    final String s = getText().substring(PREFIX.length()).trim();

    try {
      final Integer delay = Integer.valueOf(s);
      context.getRobot().delay(delay.intValue());
    }
    catch (NumberFormatException e) {
      dumpError(context, "Invalid delay value: " + s);
      return Promises.rejectedPromise(e);
    }

    return Promises.resolvedPromise();
  }
}