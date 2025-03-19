// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PresentableChange {
  @NotNull
  FilePath getFilePath();

  @NotNull
  FileStatus getFileStatus();

  default @Nullable ChangesBrowserNode.Tag getTag() {
    return null;
  }
}
