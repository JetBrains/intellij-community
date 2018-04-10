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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.Iterator;

/**
 * @see LibraryTablesRegistrar#getLibraryTable(com.intellij.openapi.project.Project)
 * @author dsl
 */
public interface LibraryTable {
  @NotNull
  Library[] getLibraries();

  @NotNull
  Library createLibrary();

  @NotNull
  Library createLibrary(@NonNls String name);

  void removeLibrary(@NotNull Library library);

  @NotNull
  Iterator<Library> getLibraryIterator();

  @Nullable
  Library getLibraryByName(@NotNull String name);

  @NotNull
  String getTableLevel();

  @NotNull
  LibraryTablePresentation getPresentation();

  default boolean isEditable() {
    return true;
  }

  /**
   * Returns the interface which allows to create or removed libraries from the table.
   * <strong>The returned model must be either committed {@link ModifiableModel#commit()} or disposed {@link com.intellij.openapi.util.Disposer#dispose(Disposable)}</strong>
   *
   * @return the modifiable library table model.
   */
  @NotNull
  ModifiableModel getModifiableModel();

  void addListener(@NotNull Listener listener);
  
  void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable);

  void removeListener(@NotNull Listener listener);

  interface ModifiableModel extends Disposable {
    @NotNull
    Library createLibrary(String name);

    @NotNull
    Library createLibrary(String name, @Nullable PersistentLibraryKind type);

    @NotNull 
    Library createLibrary(String name, @Nullable PersistentLibraryKind type, @Nullable ProjectModelExternalSource externalSource);

    void removeLibrary(@NotNull Library library);

    void commit();

    @NotNull
    Iterator<Library> getLibraryIterator();

    @Nullable
    Library getLibraryByName(@NotNull String name);

    @NotNull
    Library[] getLibraries();

    boolean isChanged();
  }

  interface Listener extends EventListener {
    default void afterLibraryAdded(@NotNull Library newLibrary) {
    }

    default void afterLibraryRenamed(@NotNull Library library) {
    }

    default void beforeLibraryRemoved(@NotNull Library library) {
    }

    default void afterLibraryRemoved(@NotNull Library library) {
    }
  }
}
