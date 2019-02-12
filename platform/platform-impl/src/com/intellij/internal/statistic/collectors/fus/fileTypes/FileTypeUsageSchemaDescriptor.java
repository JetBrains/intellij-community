// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileTypeUsageSchemaDescriptor {
  /**
   * Used to categorise file types usage statistics.
   * If the file has some generic file type (e.g. XML), this method allow specifying the 'schema' more precisely,
   * e.g `Maven` or `Spring`.
   */
  @Nullable
  String describeSchema(@NotNull VirtualFile file);
}
