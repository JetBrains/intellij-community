/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.project.model.impl.library;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author nik
 */
public class JpsLibraryTableImpl implements LibraryTable, Disposable {
  private final JpsLibrariesModel myModel;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private final String myTableLevel;
  private LibraryTablePresentation myPresentation;

  public JpsLibraryTableImpl(JpsLibraryCollection libraryCollection, String level) {
    myTableLevel = level;
    myModel = new JpsLibrariesModel(libraryCollection);
  }

  @NotNull
  @Override
  public Library[] getLibraries() {
    return myModel.getLibraries();
  }

  @NotNull
  @Override
  public Iterator<Library> getLibraryIterator() {
    return myModel.getLibraryIterator();
  }

  @Override
  public Library getLibraryByName(@NotNull String name) {
    return myModel.getLibraryByName(name);
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public Library createLibrary() {
    return createLibrary(null);
  }

  @Override
  public Library createLibrary(@NonNls String name) {
    final ModifiableModel model = getModifiableModel();
    final Library library = model.createLibrary(name);
    model.commit();
    return library;
  }

  @Override
  public void removeLibrary(@NotNull Library library) {
    final ModifiableModel model = getModifiableModel();
    model.removeLibrary(library);
    model.commit();
  }

  @Override
  public void dispose() {
    for (Library library : getLibraries()) {
      Disposer.dispose(library);
    }
  }

  @NotNull
  @Override
  public ModifiableModel getModifiableModel() {
    return new JpsLibrariesModel(myModel.myJpsLibraries);
  }

  @Override
  public boolean isEditable() {
    return true;
  }

  @Override
  public String getTableLevel() {
    return myTableLevel;
  }

  @Override
  public LibraryTablePresentation getPresentation() {
    return myPresentation;
  }

  private class JpsLibrariesModel implements LibraryTableBase.ModifiableModel {
    private final JpsLibraryCollection myJpsLibraries;
    private final List<JpsLibraryDelegate> myLibraries;

    private JpsLibrariesModel(JpsLibraryCollection libraryCollection) {
      myLibraries = new ArrayList<>();
      myJpsLibraries = libraryCollection;
      for (JpsLibrary library : libraryCollection.getLibraries()) {
        myLibraries.add(new JpsLibraryDelegate(library, JpsLibraryTableImpl.this));
      }
    }

    @Override
    public Library createLibrary(String name) {
      return createLibrary(name, null);
    }

    @Override
    public Library createLibrary(String name, @Nullable PersistentLibraryKind type) {
      throw new UnsupportedOperationException("'createLibrary' not implemented in " + getClass().getName());
    }

    @NotNull
    @Override
    public Iterator<Library> getLibraryIterator() {
      return Collections.<Library>unmodifiableList(myLibraries).iterator();
    }

    @Override
    public void removeLibrary(@NotNull Library library) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Library[] getLibraries() {
      return myLibraries.toArray(new Library[myLibraries.size()]);
    }

    @Override
    public Library getLibraryByName(@NotNull String name) {
      for (JpsLibraryDelegate library : myLibraries) {
        if (name.equals(library.getName())) {
          return library;
        }
      }
      return null;
    }

    @Override
    public void commit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean isChanged() {
      return false;
    }
  }
}
