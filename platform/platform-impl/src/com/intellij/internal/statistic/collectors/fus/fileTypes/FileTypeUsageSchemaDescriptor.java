// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface FileTypeUsageSchemaDescriptor {
  /**
   * @deprecated use {@link FileTypeUsageSchemaDescriptor#describes(Project, VirtualFile)}
   */
  @Deprecated(forRemoval = true)
  default boolean describes(@NotNull VirtualFile file) {
    return false;
  }

  /**
   * Is used to categorise file types usage statistics.
   * If a file has some generic file type (e.g. XML), this method allow specifying its 'schema' more precisely, e.g `Maven` or `Spring`.
   *
   * @return true if the given file has the schema name, given in the `schema` attribute of the `FileTypeUsageSchemaDescriptor` extension.
   */
  default boolean describes(@NotNull Project project, @NotNull VirtualFile file) {
    return describes(file);
  }
}
