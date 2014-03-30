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

  @NotNull
  public abstract LibraryTable getLibraryTable();

  @NotNull
  public abstract LibraryTable getLibraryTable(@NotNull Project project);

  @Nullable
  public abstract LibraryTable getLibraryTableByLevel(@NonNls String level, @NotNull Project project);

  public abstract void registerLibraryTable(@NotNull LibraryTable libraryTable);

  public abstract List<LibraryTable> getCustomLibraryTables();
}