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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;

/**
 *  @author dsl
 */
@ApiStatus.Internal
public class ModuleLibraryTable implements LibraryTable, LibraryTable.ModifiableModel {
  @NotNull
  private final RootModelImpl myRootModel;
  @NotNull
  private final ProjectRootManagerImpl myProjectRootManager;
  public static final LibraryTablePresentation MODULE_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    @NotNull
    @Override
    public String getDisplayName(boolean plural) {
      return ProjectModelBundle.message("module.library.display.name", plural ? 2 : 1);
    }

    @NotNull
    @Override
    public String getDescription() {
      return ProjectModelBundle.message("libraries.node.text.module");
    }

    @NotNull
    @Override
    public String getLibraryTableEditorTitle() {
      return ProjectModelBundle.message("library.configure.module.title");
    }
  };

  ModuleLibraryTable(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    myRootModel = rootModel;
    myProjectRootManager = projectRootManager;
  }

  @Override
  public Library @NotNull [] getLibraries() {
    final ArrayList<Library> result = new ArrayList<>();
    final Iterator<Library> libraryIterator = getLibraryIterator();
    ContainerUtil.addAll(result, libraryIterator);
    return result.toArray(Library.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public Library createLibrary() {
    return createLibrary(null);
  }

  @NotNull
  @Override
  public Library createLibrary(String name) {
    return createLibrary(name, null);
  }

  @NotNull
  @Override
  public Library createLibrary(String name, @Nullable PersistentLibraryKind type) {
    return createLibrary(name, type, null);
  }

  @NotNull
  @Override
  public Library createLibrary(String name, @Nullable PersistentLibraryKind kind, @Nullable ProjectModelExternalSource externalSource) {
    ModuleLibraryOrderEntryImpl orderEntry = new ModuleLibraryOrderEntryImpl(name, kind, myRootModel, myProjectRootManager, externalSource);
    myRootModel.addOrderEntry(orderEntry);
    return orderEntry.getLibrary();
  }

  @Override
  public void removeLibrary(@NotNull Library library) {
    final Iterator<OrderEntry> orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      OrderEntry orderEntry = orderIterator.next();
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry) orderEntry;
        if (libraryOrderEntry.isModuleLevel()) {
          if (library.equals(libraryOrderEntry.getLibrary())) {
            myRootModel.removeOrderEntry(orderEntry);
            //Disposer.dispose(library);
            return;
          }
        }
      }
    }
  }

  @Override
  @NotNull
  public Iterator<Library> getLibraryIterator() {
    FilteringIterator<OrderEntry, LibraryOrderEntry> filteringIterator =
      new FilteringIterator<>(myRootModel.getOrderIterator(), entry -> entry instanceof LibraryOrderEntry &&
                                                                       ((LibraryOrderEntry)entry).isModuleLevel() &&
                                                                       ((LibraryOrderEntry)entry).getLibrary() != null);
    return ContainerUtil.mapIterator(filteringIterator, LibraryOrderEntry::getLibrary);
  }

  @NotNull
  @Override
  public String getTableLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  @NotNull
  @Override
  public LibraryTablePresentation getPresentation() {
    return MODULE_LIBRARY_TABLE_PRESENTATION;
  }

  @Override
  @Nullable
  public Library getLibraryByName(@NotNull String name) {
    final Iterator<Library> libraryIterator = getLibraryIterator();
    while (libraryIterator.hasNext()) {
      Library library = libraryIterator.next();
      if (name.equals(library.getName())) return library;
    }
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable) {
    throw new UnsupportedOperationException("Method addListener is not yet implemented in " + getClass().getName());
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Module getModule() {
    return myRootModel.getModule();
  }

  @Override
  public void commit() {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isChanged() {
    return myRootModel.isChanged();
  }

  @NotNull
  @Override
  public ModifiableModel getModifiableModel() {
    return this;
  }
}
