// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

@ApiStatus.Internal
public final class StopCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "stop";

  public StopCommand(String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    context.message("Stopped", getLine());
    return Promises.resolvedPromise();
  }

  @Override
  public boolean canGoFurther() {
    return false;
  }
}