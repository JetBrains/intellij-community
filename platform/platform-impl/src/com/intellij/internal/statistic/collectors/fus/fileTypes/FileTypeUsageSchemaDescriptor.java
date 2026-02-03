// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface FileTypeUsageSchemaDescriptor {
  /**
   * Is used to categorize file types usage statistics.
   * If a file has some generic file type (e.g., XML), this method allows specifying its 'schema' more precisely, e.g. `Maven` or `Spring`.
   *
   * @return true if the given file has the schema name, given in the `schema` attribute of the `FileTypeUsageSchemaDescriptor` extension.
   */
  boolean describes(@NotNull Project project, @NotNull VirtualFile file);
}
