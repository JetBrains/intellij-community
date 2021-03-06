// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
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
    return ApplicationManager.getApplication().getService(ApplicationLibraryTable.class);
  }

  public ApplicationLibraryTable() {
    //this is needed to ensure that VirtualFilePointerManager is initialized before ApplicationLibraryTable and therefore disposed after it;
    //otherwise VirtualFilePointerManagerImpl.dispose will report non-disposed pointers from global libraries
    VirtualFilePointerManager.getInstance();
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
