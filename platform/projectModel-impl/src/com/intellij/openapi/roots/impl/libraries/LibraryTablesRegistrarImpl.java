// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements Disposable {
  private static final ExtensionPointName<CustomLibraryTableDescription> CUSTOM_TABLES_EP = new ExtensionPointName<>("com.intellij.customLibraryTable");
  private final Map<String, LibraryTableBase> myCustomLibraryTables = new ConcurrentHashMap<>();
  private volatile boolean myExtensionsLoaded = false;
  private final Object myExtensionsLoadingLock = new Object();

  LibraryTablesRegistrarImpl() {
    //this is needed to ensure that VirtualFilePointerManager is initialized before custom library tables and therefore disposed after them;
    //otherwise VirtualFilePointerManagerImpl.dispose will report non-disposed pointers from custom library tables
    VirtualFilePointerManager.getInstance();
  }

  @Override
  public @NotNull LibraryTable getLibraryTable() {
    return ApplicationLibraryTable.getApplicationTable();
  }

  @Override
  public @NotNull LibraryTable getLibraryTable(@NotNull Project project) {
    return project.getService(ProjectLibraryTable.class);
  }

  @Override
  public LibraryTable getLibraryTableByLevel(String level, @NotNull Project project) {
    return switch (level) {
      case LibraryTablesRegistrar.PROJECT_LEVEL -> getLibraryTable(project);
      case LibraryTablesRegistrar.APPLICATION_LEVEL -> getLibraryTable();
      default -> getCustomLibraryTableByLevel(level);
    };
  }

  @Override
  public @Nullable LibraryTable getCustomLibraryTableByLevel(String level) {
    return getCustomLibrariesMap().get(level);
  }

  public @NotNull Map<String, LibraryTableBase> getCustomLibrariesMap() {
    if (myExtensionsLoaded) {
      return myCustomLibraryTables;
    }

    synchronized (myExtensionsLoadingLock) {
      if (!myExtensionsLoaded) {
        CUSTOM_TABLES_EP.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {
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
    return myCustomLibraryTables;
  }

  @Override
  public @NotNull List<LibraryTable> getCustomLibraryTables() {
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