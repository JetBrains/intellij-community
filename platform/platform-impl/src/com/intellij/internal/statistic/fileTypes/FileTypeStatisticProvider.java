// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.fileTypes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

public interface FileTypeStatisticProvider extends Disposable {
  ExtensionPointName<FileTypeStatisticProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeStatisticProvider");

  @NotNull
  String getPluginId();

  boolean accept(@NotNull EditorFactoryEvent event, @NotNull FileType fileType);

  @Override
  default void dispose() {
  }
}

