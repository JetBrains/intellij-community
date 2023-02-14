// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.io.File;

public interface PlaybackCommand {
  Promise<Object> execute(PlaybackContext context);
  boolean canGoFurther();

  default @Nullable File getScriptDir() {
    return null;
  }
}