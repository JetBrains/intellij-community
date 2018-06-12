// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.StageInfo;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class PopStage extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "endTest";

  public PopStage(String text, int line) {
    super(text, line);
  }

  @Override
  protected Promise<Object> _execute(PlaybackContext context) {
    StageInfo stage = context.popStage();
    if (stage != null) {
      context.test("Test finished OK: " + stage.getName(), getLine());
      context.addPassed(stage);
    }
    return Promises.resolvedPromise();
  }

  @Override
  protected boolean isToDumpCommand() {
    return false;
  }
}
