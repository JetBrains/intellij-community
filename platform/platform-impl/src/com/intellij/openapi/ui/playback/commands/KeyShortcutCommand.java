// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

@ApiStatus.Internal
public final class KeyShortcutCommand extends TypeCommand {

  public static final String PREFIX = CMD_PREFIX + "[";
  public static final String POSTFIX = "]";

  public KeyShortcutCommand(String text, int line) {
    super(text, line, false);
  }

  @Override
  public @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final String one = getText().substring(PREFIX.length());
    if (!one.endsWith(POSTFIX)) {
      dumpError(context, "Expected " + "]");
      return Promises.rejectedPromise();
    }

    type(context.getRobot(), getFromShortcut(one.substring(0, one.length() - 1).trim()));

    return Promises.resolvedPromise();
  }
}