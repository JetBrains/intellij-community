/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class LibraryTablesRegistrar {
  @NonNls public static final String PROJECT_LEVEL = "project";
  @NonNls public static final String APPLICATION_LEVEL = "application";

  public static LibraryTablesRegistrar getInstance() {
    return ServiceManager.getService(LibraryTablesRegistrar.class);
  }

  /**
   * Returns the table containing application-level libraries. These libraries are shown in 'Project Structure' | 'Platform Settings' | 'Global Libraries'
   * and may be added to dependencies of modules in any project.
   */
  @NotNull
  public abstract LibraryTable getLibraryTable();

  /**
   * Returns the table containing project-level libraries for given {@code project}. These libraries are shown in 'Project Structure'
   * | 'Project Settings' | 'Libraries' and may be added to dependencies of the corresponding project's modules only.
   */
  @NotNull
  public abstract LibraryTable getLibraryTable(@NotNull Project project);

  /**
   * Returns a custom library table registered by {@link #registerLibraryTable(LibraryTable)}.
   */
  @Nullable
  public abstract LibraryTable getLibraryTableByLevel(@NonNls String level, @NotNull Project project);

  /**
   * Register a custom library table. The platform doesn't provide editors for libraries from custom tables and doesn't save their configurations
   * automatically, so a plugin providing such a table must implement this functionality on its own. Modules from custom tables may be added to
   * dependencies of modules in any project in 'Project Structure' dialog.
   */
  public abstract void registerLibraryTable(@NotNull LibraryTable libraryTable);

  public abstract List<LibraryTable> getCustomLibraryTables();
}