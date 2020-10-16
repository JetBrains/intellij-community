// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A mapper relating fold region and inlay used to implement in-place rendering of doc comments.
 */
@ApiStatus.Internal
public interface EditorInlayFoldingMapper {
  @NotNull
  static EditorInlayFoldingMapper getInstance() {
    return ApplicationManager.getApplication().getService(EditorInlayFoldingMapper.class);
  }

  @Nullable
  FoldRegion getAssociatedFoldRegion(@NotNull Inlay<?> inlay);

  @Nullable
  Inlay<?> getAssociatedInlay(@NotNull FoldRegion foldRegion);
}
