// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

final class ModuleLibraryTable extends ModuleLibraryTableBase {
  private final @NotNull RootModelImpl myRootModel;
  private final @NotNull ProjectRootManagerImpl myProjectRootManager;

  ModuleLibraryTable(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    myRootModel = rootModel;
    myProjectRootManager = projectRootManager;
  }

  @Override
  public @NotNull Library createLibrary(String name, @Nullable PersistentLibraryKind<?> kind, @Nullable ProjectModelExternalSource externalSource) {
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
  public @NotNull Iterator<Library> getLibraryIterator() {
    FilteringIterator<OrderEntry, LibraryOrderEntry> filteringIterator =
      new FilteringIterator<>(myRootModel.getOrderIterator(), entry -> entry instanceof LibraryOrderEntry &&
                                                                       ((LibraryOrderEntry)entry).isModuleLevel() &&
                                                                       ((LibraryOrderEntry)entry).getLibrary() != null);
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return filteringIterator.hasNext();
      }

      @Override
      public Library next() {
        return filteringIterator.next().getLibrary();
      }

      @Override
      public void remove() {
        filteringIterator.remove();
      }
    };
  }

  public @NotNull Module getModule() {
    return myRootModel.getModule();
  }

  @Override
  public boolean isChanged() {
    return myRootModel.isChanged();
  }
}
