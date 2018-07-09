// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class EmptyCommand extends AbstractCommand {
  public EmptyCommand(int line) {
    super("", line);
  }

  public Promise<Object> _execute(PlaybackContext context) {
    return Promises.resolvedPromise();
  }
}