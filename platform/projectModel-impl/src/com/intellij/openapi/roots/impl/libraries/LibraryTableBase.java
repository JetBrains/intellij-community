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

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class LibraryTableBase implements PersistentStateComponent<Element>, LibraryTable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.libraries.LibraryTableBase");
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private LibraryModel myModel = new LibraryModel();
  private boolean myFirstLoad = true;

  private volatile long myModificationCount;

  @NotNull
  @Override
  public ModifiableModel getModifiableModel() {
    return new LibraryModel(myModel);
  }

  @Override
  public Element getState() {
    final Element element = new Element("state");
    try {
      myModel.writeExternal(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return element;
  }

  @Override
  public void noStateLoaded() {
    myFirstLoad = false;
  }

  @Override
  public void loadState(final Element element) {
    if (myFirstLoad) {
      myModel.readExternal(element);
    }
    else {
      LibraryModel model = new LibraryModel(myModel);
      WriteAction.run(() -> {
        model.readExternal(element);
        commit(model);
      });
    }

    myFirstLoad = false;
  }

  public long getStateModificationCount() {
    return myModificationCount;
  }

  @Override
  @NotNull
  public Library[] getLibraries() {
    return myModel.getLibraries();
  }

  @Override
  @NotNull
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

  private void fireLibraryAdded(@NotNull Library library) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("fireLibraryAdded: " + library);
    }
    myDispatcher.getMulticaster().afterLibraryAdded(library);
  }

  private void fireBeforeLibraryRemoved(@NotNull Library library) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("fireBeforeLibraryRemoved: " + library);
    }
    myDispatcher.getMulticaster().beforeLibraryRemoved(library);
  }

  @Override
  public void dispose() {
    for (Library library : getLibraries()) {
      Disposer.dispose(library);
    }
  }

  @NotNull
  @Override
  public Library createLibrary() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    return createLibrary(null);
  }

  void fireLibraryRenamed(@NotNull LibraryImpl library) {
    incrementModificationCount();
    myDispatcher.getMulticaster().afterLibraryRenamed(library);
  }

  private void incrementModificationCount() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("library");
    }
    myModificationCount++;
  }

  @NotNull
  @Override
  public Library createLibrary(String name) {
    final ModifiableModel modifiableModel = getModifiableModel();
    final Library library = modifiableModel.createLibrary(name);
    modifiableModel.commit();
    return library;
  }

  @Override
  public void removeLibrary(@NotNull Library library) {
    final ModifiableModel modifiableModel = getModifiableModel();
    modifiableModel.removeLibrary(library);
    modifiableModel.commit();
  }

  private void commit(LibraryModel model) {
    myFirstLoad = false;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!model.isChanged()) {
      Disposer.dispose(model);
      return;
    }

    incrementModificationCount();
    //todo[nik] remove LibraryImpl#equals method instead of using identity sets
    Set<Library> addedLibraries = ContainerUtil.newIdentityTroveSet(model.myLibraries);
    addedLibraries.removeAll(myModel.myLibraries);
    Set<Library> removedLibraries = ContainerUtil.newIdentityTroveSet(myModel.myLibraries);
    removedLibraries.removeAll(model.myLibraries);

    for (Library library : removedLibraries) {
      fireBeforeLibraryRemoved(library);
    }

    myModel.copyFrom(model);
    for (Library library : removedLibraries) {
      Disposer.dispose(library);
      fireAfterLibraryRemoved(library);
    }
    for (Library library : addedLibraries) {
      fireLibraryAdded(library);
    }
    Disposer.dispose(model);
  }

  private void fireAfterLibraryRemoved(Library library) {
    myDispatcher.getMulticaster().afterLibraryRemoved(library);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myModel = new LibraryModel();
    myModel.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    myModel.writeExternal(element);
  }

  public class LibraryModel implements ModifiableModel, JDOMExternalizable, Listener, Disposable {
    private final ArrayList<Library> myLibraries = new ArrayList<>();
    private volatile Map<String, Library> myLibraryByNameCache;
    private boolean myWritable;

    private LibraryModel() {
      myDispatcher.addListener(this);
      myWritable = false;
    }

    private LibraryModel(LibraryModel that) {
      myDispatcher.addListener(this);
      myWritable = true;
      myLibraries.addAll(that.myLibraries);
    }

    @Override
    public void commit() {
      LibraryTableBase.this.commit(this);
      myWritable = false;
    }

    @Override
    public void dispose() {
      myDispatcher.removeListener(this);
    }

    @Override
    @NotNull
    public Iterator<Library> getLibraryIterator() {
      return Collections.unmodifiableList(myLibraries).iterator();
    }

    @Override
    @Nullable
    public Library getLibraryByName(@NotNull String name) {
      Map<String, Library> cache = myLibraryByNameCache;
      if (cache == null) {
        cache = new HashMap<>();
        for (Library library : myLibraries) {
          cache.put(library.getName(), library);
        }
        myLibraryByNameCache = cache;
      }
      Library library = cache.get(name);
      if (library != null) {
        return library;
      }

      @NonNls final String libraryPrefix = "library.";
      final String libPath = System.getProperty(libraryPrefix + name);
      if (libPath != null) {
        final LibraryImpl libraryFromProperty = new LibraryImpl(name, null, LibraryTableBase.this, null, null);
        libraryFromProperty.addRoot(libPath, OrderRootType.CLASSES);
        return libraryFromProperty;
      }
      return null;
    }


    @Override
    @NotNull
    public Library[] getLibraries() {
      return myLibraries.toArray(new Library[myLibraries.size()]);
    }

    private void assertWritable() {
      LOG.assertTrue(myWritable);
    }

    @NotNull
    @Override
    public Library createLibrary(String name) {
      return createLibrary(name, null);
    }

    @NotNull
    @Override
    public Library createLibrary(String name, @Nullable PersistentLibraryKind kind) {
      return createLibrary(name, kind, null);
    }

    @NotNull
    @Override
    public Library createLibrary(String name, @Nullable PersistentLibraryKind kind, @Nullable ProjectModelExternalSource externalSource) {
      assertWritable();
      final LibraryImpl library = new LibraryImpl(name, kind, LibraryTableBase.this, null, externalSource);
      myLibraries.add(library);
      myLibraryByNameCache = null;
      return library;
    }

    @Override
    public void removeLibrary(@NotNull Library library) {
      incrementModificationCount();

      assertWritable();
      myLibraries.remove(library);
      myLibraryByNameCache = null;
    }

    @Override
    public boolean isChanged() {
      if (!myWritable) return false;
      Set<Library> thisLibraries = new HashSet<>(myLibraries);
      Set<Library> thatLibraries = new HashSet<>(myModel.myLibraries);
      return !thisLibraries.equals(thatLibraries);
    }

    @Override
    public void readExternal(Element element) {
      myLibraries.clear();

      final List<Element> libraryElements = element.getChildren(LibraryImpl.ELEMENT);
      for (Element libraryElement : libraryElements) {
        final LibraryImpl library = new LibraryImpl(LibraryTableBase.this, libraryElement, null);
        if (library.getName() != null) {
          myLibraries.add(library);
          fireLibraryAdded(library);
        }
        else {
          Disposer.dispose(library);
        }
      }
      myLibraryByNameCache = null;
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library) {
      myLibraryByNameCache = null;
    }

    @Override
    public void writeExternal(Element element) {
      final List<Library> libraries = ContainerUtil.findAll(myLibraries, library -> !((LibraryEx)library).isDisposed());

      // todo: do not sort if project is directory-based
      ContainerUtil.sort(libraries, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));

      for (final Library library : libraries) {
        if (library.getName() != null) {
          library.writeExternal(element);
        }
      }
    }

    void copyFrom(LibraryModel model) {
      myLibraries.clear();
      myLibraries.addAll(model.myLibraries);
      myLibraryByNameCache = null;
    }
  }
}
