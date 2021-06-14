// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JumpTask {
  final @NotNull String title;
  final @NotNull String executablePath;
  final @Nullable String executableArgs;


  JumpTask(@NotNull String title, @NotNull String executablePath, @Nullable String executableArgs) {
    this.title = title;
    this.executablePath = executablePath;
    this.executableArgs = executableArgs;
  }
}