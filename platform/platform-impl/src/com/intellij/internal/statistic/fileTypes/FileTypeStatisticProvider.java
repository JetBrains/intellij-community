// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.fileTypes;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated If really needed use {@link com.intellij.ide.plugins.StandalonePluginUpdateChecker} instead.
 */
@Deprecated
public interface FileTypeStatisticProvider {
  @NotNull
  @NonNls
  String getPluginId();

  boolean accept(@NotNull Editor editor, @NotNull FileType fileType);
}

