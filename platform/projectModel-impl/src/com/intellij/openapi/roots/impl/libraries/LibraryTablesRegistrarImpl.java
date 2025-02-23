// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements Disposable {
  private final Map<String, LibraryTable> customLibraryTables = new ConcurrentHashMap<>();
  private volatile boolean extensionLoaded = false;
  private final Object extensionLoadingLock = new Object();

  LibraryTablesRegistrarImpl() {
    //this is needed to ensure that VirtualFilePointerManager is initialized before custom library tables and therefore disposed after them;
    //otherwise VirtualFilePointerManagerImpl.dispose will report non-disposed pointers from custom library tables
    VirtualFilePointerManager.getInstance();
  }

  @Override
  public @NotNull LibraryTable getLibraryTable() {
    // todo: IJPL-175225 add possibility to select non-local global library tables
    return GlobalLibraryTableBridge.Companion.getInstance(LocalEelDescriptor.INSTANCE);
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

  private @NotNull Map<String, LibraryTable> getCustomLibrariesMap() {
    if (extensionLoaded) {
      return customLibraryTables;
    }

    synchronized (extensionLoadingLock) {
      if (!extensionLoaded) {
        CustomLibraryTableDescription.CUSTOM_TABLES_EP.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {
          @Override
          public void extensionAdded(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
            LibraryTable table = new CustomLibraryTableImpl(extension.getTableLevel(), extension.getPresentation());
            customLibraryTables.put(extension.getTableLevel(), table);
          }

          @Override
          public void extensionRemoved(@NotNull CustomLibraryTableDescription extension, @NotNull PluginDescriptor pluginDescriptor) {
            LibraryTable table = customLibraryTables.remove(extension.getTableLevel());
            if (table instanceof Disposable disposable) {
              Disposer.dispose(disposable);
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
    for (LibraryTable table : customLibraryTables.values()) {
      if (table instanceof Disposable disposable) {
        Disposer.dispose(disposable);
      }
    }
    customLibraryTables.clear();
  }
}