/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This class is for internal use, in order to get instance of the application-level library table, use {@link LibraryTablesRegistrar#getLibraryTable()}
 */
@ApiStatus.Internal
public class ApplicationLibraryTable extends LibraryTableBase {
  private static final LibraryTablePresentation GLOBAL_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    @NotNull
    @Override
    public String getDisplayName(boolean plural) {
      return ProjectModelBundle.message("global.library.display.name", plural ? 2 : 1);
    }

    @NotNull
    @Override
    public String getDescription() {
      return ProjectModelBundle.message("libraries.node.text.ide");
    }

    @NotNull
    @Override
    public String getLibraryTableEditorTitle() {
      return ProjectModelBundle.message("library.configure.global.title");
    }
  };

  public static ApplicationLibraryTable getApplicationTable() {
    return ServiceManager.getService(ApplicationLibraryTable.class);
  }

  @NotNull
  @Override
  public String getTableLevel() {
    return LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  @NotNull
  @Override
  public LibraryTablePresentation getPresentation() {
    return GLOBAL_LIBRARY_TABLE_PRESENTATION;
  }

  public static String getExternalFileName() {
    return "applicationLibraries";
  }
}
