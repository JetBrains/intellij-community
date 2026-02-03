// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;

@ApiStatus.Internal
public final class CdCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "cd";
  private final String myDir;

  public CdCommand(String text, int line) {
    super(text, line);
    myDir = text.substring(PREFIX.length()).trim();
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    File file = context.getPathMacro().resolveFile(myDir, context.getBaseDir());
    if (!file.exists()) {
      context.message("Cannot cd, directory doesn't exist: " + file.getAbsoluteFile(), getLine());
      return Promises.rejectedPromise();
    }

    try {
      context.setBaseDir(file.getCanonicalFile());
    }
    catch (IOException e) {
      context.setBaseDir(file);
    }

    context.message("{base.dir} set to " + context.getBaseDir().getAbsolutePath(), getLine());
    return Promises.resolvedPromise();
  }
}
