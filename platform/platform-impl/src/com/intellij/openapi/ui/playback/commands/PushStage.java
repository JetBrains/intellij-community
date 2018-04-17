// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.StageInfo;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class PushStage extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "startTest";

  public PushStage(String text, int line) {
    super(text, line);
  }

  @Override
  protected Promise<Object> _execute(PlaybackContext context) {
    String name = getText().substring(PREFIX.length()).trim();
    context.test("Test started: " + name, getLine());
    context.pushStage(new StageInfo(name));
    return Promises.resolvedPromise();
  }

  @Override
  protected boolean isToDumpCommand() {
    return false;
  }
}
