/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements Disposable {
  private static final ExtensionPointName<CustomLibraryTableDescription> CUSTOM_TABLES_EP = ExtensionPointName.create("com.intellij.customLibraryTable");
  private final Map<String, LibraryTableBase> myCustomLibraryTables = new ConcurrentHashMap<>();
  private volatile boolean myExtensionsLoaded = false;
  private final Object myExtensionsLoadingLock = new Object();

  @Override
  @NotNull
  public LibraryTable getLibraryTable() {
    return ApplicationLibraryTable.getApplicationTable();
  }

  @Override
  @NotNull
  public LibraryTable getLibraryTable(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectLibraryTable.class);
  }

  @Override
  public LibraryTable getLibraryTableByLevel(String level, @NotNull Project project) {
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) return getLibraryTable(project);
    if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) return getLibraryTable();
    return getCustomLibraryTableByLevel(level);
  }

  @Override
  public @Nullable LibraryTable getCustomLibraryTableByLevel(String level) {
    return getCustomLibrariesMap().get(level);
  }

  public Map<String, LibraryTableBase> getCustomLibrariesMap() {
    if (!myExtensionsLoaded) {
      synchronized (myExtensionsLoadingLock) {
        if (!myExtensionsLoaded) {
          CUSTOM_TABLES_EP.getPoint().addExtensionPointListener(new ExtensionPointListener<CustomLibraryTableDescription>() {
            @Override
            public void extensionAdded(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
              LibraryTableBase table = new CustomLibraryTableImpl(extension.getTableLevel(), extension.getPresentation());
              myCustomLibraryTables.put(extension.getTableLevel(), table);
            }

            @Override
            public void extensionRemoved(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
              LibraryTableBase table = myCustomLibraryTables.remove(extension.getTableLevel());
              if (table != null) {
                Disposer.dispose(table);
              }
            }
          }, true, null);
          myExtensionsLoaded = true;
        }
      }
    }
    return myCustomLibraryTables;
  }

  @Override
  public void registerLibraryTable(@NotNull LibraryTable libraryTable) {
    String tableLevel = libraryTable.getTableLevel();
    final LibraryTable oldTable = myCustomLibraryTables.put(tableLevel, (LibraryTableBase)libraryTable);
    if (oldTable != null) {
      throw new IllegalArgumentException("Library table '" + tableLevel + "' already registered.");
    }
  }

  @NotNull
  @Override
  public List<LibraryTable> getCustomLibraryTables() {
    return new SmartList<>(getCustomLibrariesMap().values());
  }

  @Override
  public void dispose() {
    for (LibraryTableBase value : myCustomLibraryTables.values()) {
      Disposer.dispose(value);
    }
    myCustomLibraryTables.clear();
  }
}