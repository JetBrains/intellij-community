// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.NonExtendable
public abstract class LibraryTablesRegistrar {
  @NonNls public static final String PROJECT_LEVEL = "project";
  @NonNls public static final String APPLICATION_LEVEL = "application";

  public static LibraryTablesRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(LibraryTablesRegistrar.class);
  }

  /**
   * Returns the table containing application-level libraries. These libraries are shown in 'Project Structure' | 'Platform Settings' | 'Global Libraries'
   * and may be added to dependencies of modules in any project.
   */
  public abstract @NotNull LibraryTable getLibraryTable();

  /**
   * Returns the table containing project-level libraries for given {@code project}. These libraries are shown in 'Project Structure'
   * | 'Project Settings' | 'Libraries' and may be added to dependencies of the corresponding project's modules only.
   */
  public abstract @NotNull LibraryTable getLibraryTable(@NotNull Project project);

  /**
   * Returns the standard or a custom library table registered by {@link #registerLibraryTable(LibraryTable)} or via {@link CustomLibraryTableDescription}.
   */
  public abstract @Nullable LibraryTable getLibraryTableByLevel(@NonNls String level, @NotNull Project project);

  /**
   * Returns a custom library table registered by {@link #registerLibraryTable(LibraryTable)} or via {@link CustomLibraryTableDescription}.
   */
  public abstract @Nullable LibraryTable getCustomLibraryTableByLevel(@NonNls String level);

  /**
   * @deprecated use {@link CustomLibraryTableDescription} extension point instead
   */
  @Deprecated(forRemoval = true)
  public abstract void registerLibraryTable(@NotNull LibraryTable libraryTable);

  public abstract @NotNull List<LibraryTable> getCustomLibraryTables();
}