// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ProjectLibraryTable extends LibraryTable {
  /**
   * @deprecated use {@link com.intellij.openapi.roots.libraries.LibraryTablesRegistrar#getLibraryTable(Project)} instead
   */
  @Deprecated
  static LibraryTable getInstance(Project project) {
    return project.getService(ProjectLibraryTable.class);
  }

  @NotNull
  Project getProject();
}
