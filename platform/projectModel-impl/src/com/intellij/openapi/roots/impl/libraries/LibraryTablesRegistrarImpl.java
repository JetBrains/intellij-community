// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements Disposable {
  private static final ExtensionPointName<CustomLibraryTableDescription> CUSTOM_TABLES_EP = new ExtensionPointName<>("com.intellij.customLibraryTable");
  private final Map<String, LibraryTableBase> customLibraryTables = new ConcurrentHashMap<>();
  private volatile boolean extensionLoaded = false;
  private final Object extensionLoadingLock = new Object();

  LibraryTablesRegistrarImpl() {
    //this is needed to ensure that VirtualFilePointerManager is initialized before custom library tables and therefore disposed after them;
    //otherwise VirtualFilePointerManagerImpl.dispose will report non-disposed pointers from custom library tables
    VirtualFilePointerManager.getInstance();
  }

  @Override
  public @NotNull LibraryTable getLibraryTable() {
    return GlobalLibraryTableBridge.Companion.getInstance();
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
    if (extensionLoaded) {
      return customLibraryTables;
    }

    synchronized (extensionLoadingLock) {
      if (!extensionLoaded) {
        CUSTOM_TABLES_EP.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {
          @Override
          public void extensionAdded(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
            LibraryTableBase table = new CustomLibraryTableImpl(extension.getTableLevel(), extension.getPresentation());
            customLibraryTables.put(extension.getTableLevel(), table);
          }

          @Override
          public void extensionRemoved(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
            LibraryTableBase table = customLibraryTables.remove(extension.getTableLevel());
            if (table != null) {
              Disposer.dispose(table);
            }
          }
        }, true, null);
        extensionLoaded = true;
      }
    }
    return customLibraryTables;
  }

  @Override
  public @NotNull List<LibraryTable> getCustomLibraryTables() {
    return List.copyOf(getCustomLibrariesMap().values());
  }

  @Override
  public void dispose() {
    for (LibraryTableBase value : customLibraryTables.values()) {
      Disposer.dispose(value);
    }
    customLibraryTables.clear();
  }
}