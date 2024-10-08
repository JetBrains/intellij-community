// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

@ApiStatus.Internal
public final class DelayCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "delay";

  public DelayCommand(String text, int line) {
    super(text, line);
  }

  @Override
  public @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final String s = getText().substring(PREFIX.length()).trim();

    try {
      final int delay = Integer.parseInt(s);
      context.getRobot().delay(delay);
    }
    catch (NumberFormatException e) {
      dumpError(context, "Invalid delay value: " + s);
      return Promises.rejectedPromise(e);
    }

    return Promises.resolvedPromise();
  }
}