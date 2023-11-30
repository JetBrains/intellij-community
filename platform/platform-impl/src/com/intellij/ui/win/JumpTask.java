// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.win;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JumpTask {
  final @NotNull String title;
  final @NotNull String executablePath;
  final @Nullable String executableArgs;
  final @Nullable String tooltip;


  JumpTask(@NotNull String title, @NotNull String executablePath, @Nullable String executableArgs, @Nullable String tooltip) {
    this.title = title;
    this.executablePath = executablePath;
    this.executableArgs = executableArgs;
    this.tooltip = tooltip;
  }
}