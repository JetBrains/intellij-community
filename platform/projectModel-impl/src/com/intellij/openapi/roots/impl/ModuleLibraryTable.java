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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 *  @author dsl
 */
class ModuleLibraryTable extends ModuleLibraryTableBase {
  @NotNull
  protected final RootModelImpl myRootModel;
  @NotNull
  private final ProjectRootManagerImpl myProjectRootManager;

  ModuleLibraryTable(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    myRootModel = rootModel;
    myProjectRootManager = projectRootManager;
  }

  @NotNull
  @Override
  public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> kind, @Nullable ProjectModelExternalSource externalSource) {
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
  public Module getModule() {
    return myRootModel.getModule();
  }

  @Override
  public boolean isChanged() {
    return myRootModel.isChanged();
  }
}
