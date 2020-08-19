// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.fileTypes;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface FileTypeStatisticProvider {
  @NotNull @NonNls
  String getPluginId();

  default boolean accept(@NotNull Editor editor, @NotNull FileType fileType) {
    return accept(new EditorFactoryEvent(EditorFactory.getInstance(), editor), fileType);
  }

  /**
   * @deprecated use {@link #accept(Editor, FileType)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  default boolean accept(@NotNull EditorFactoryEvent event, @NotNull FileType fileType) {
    return false;
  }
}

