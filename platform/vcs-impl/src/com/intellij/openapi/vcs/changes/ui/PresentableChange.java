// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * @deprecated Use {@link #getTag()} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default Object getPopupTag() {
    return null;
  }

  @Nullable
  default ChangesBrowserNode.Tag getTag() {
    return ChangesBrowserNode.WrapperTag.wrap(getPopupTag());
  }
}
