// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    @Override
    public @NotNull String getDisplayName(boolean plural) {
      return ProjectModelBundle.message("module.library.display.name", plural ? 2 : 1);
    }

    @Override
    public @NotNull String getDescription() {
      return ProjectModelBundle.message("libraries.node.text.module");
    }

    @Override
    public @NotNull String getLibraryTableEditorTitle() {
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

  @Override
  public @NotNull Library createLibrary() {
    return createLibrary(null);
  }

  @Override
  public @NotNull Library createLibrary(String name) {
    return createLibrary(name, null);
  }

  @Override
  public @NotNull Library createLibrary(String name, @Nullable PersistentLibraryKind<?> type) {
    return createLibrary(name, type, null);
  }

  @Override
  public @NotNull String getTableLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  @Override
  public @NotNull LibraryTablePresentation getPresentation() {
    return MODULE_LIBRARY_TABLE_PRESENTATION;
  }

  @Override
  public @Nullable Library getLibraryByName(@NotNull String name) {
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

  @Override
  public @NotNull ModifiableModel getModifiableModel() {
    return this;
  }
}
