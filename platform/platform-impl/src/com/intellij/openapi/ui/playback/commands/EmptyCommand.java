// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

@ApiStatus.Internal
public final class EmptyCommand extends AbstractCommand {
  public EmptyCommand(int line) {
    super("", line);
  }

  @Override
  public @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    return Promises.resolvedPromise();
  }
}