// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.playback;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface PlaybackCommand {
  CompletableFuture<?> execute(PlaybackContext context);
  boolean canGoFurther();

  default @Nullable File getScriptDir() {
    return null;
  }
}