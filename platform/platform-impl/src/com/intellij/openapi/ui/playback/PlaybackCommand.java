// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback;

import org.jetbrains.concurrency.Promise;

import java.io.File;

public interface PlaybackCommand {
  Promise<Object> execute(PlaybackContext context);
  boolean canGoFurther();

  File getScriptDir();
}