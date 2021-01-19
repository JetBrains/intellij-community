// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ApiStatus.Internal
public abstract class ModuleLibraryTableBase implements LibraryTable, LibraryTable.ModifiableModel {
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

  public ModuleLibraryTableBase() {
  }

  @Override
  public Library @NotNull [] getLibraries() {
    List<Library> result = new ArrayList<>();
    Iterator<Library> libraryIterator = getLibraryIterator();
    while (libraryIterator.hasNext()) {
      result.add(libraryIterator.next());
    }
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
  public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> type) {
    return createLibrary(name, type, null);
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

  @Override
  public void commit() {
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public ModifiableModel getModifiableModel() {
    return this;
  }
}
