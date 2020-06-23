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
   * Returns the standard or a custom library table registered by {@link #registerLibraryTable(LibraryTable)} or via {@link CustomLibraryTableDescription}.
   */
  @Nullable
  public abstract LibraryTable getLibraryTableByLevel(@NonNls String level, @NotNull Project project);

  /**
   * Returns a custom library table registered by {@link #registerLibraryTable(LibraryTable)} or via {@link CustomLibraryTableDescription}.
   */
  @Nullable
  public abstract LibraryTable getCustomLibraryTableByLevel(@NonNls String level);

  /**
   * @deprecated use {@link CustomLibraryTableDescription} extension point instead
   */
  @Deprecated
  public abstract void registerLibraryTable(@NotNull LibraryTable libraryTable);

  @NotNull
  public abstract List<LibraryTable> getCustomLibraryTables();
}