// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;

public interface ProjectLibraryTable extends LibraryTable {
  static LibraryTable getInstance(Project project) {
    return ServiceManager.getService(project, ProjectLibraryTable.class);
  }

  Project getProject();
}
