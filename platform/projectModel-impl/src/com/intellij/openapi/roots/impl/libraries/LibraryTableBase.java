// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.util.*;

/**
 * This class isn't used in the new implementation of project model, which is based on {@link com.intellij.workspaceModel.ide Workspace Model}.
 * It shouldn't be used directly, its interface {@link LibraryTable} should be used instead.
 */
@ApiStatus.Internal
public abstract class LibraryTableBase implements PersistentStateComponent<Element>, LibraryTable, Disposable {
  private static final Logger LOG = Logger.getInstance(LibraryTableBase.class);
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private LibraryModel myModel = new LibraryModel();
  private boolean myFirstLoad = true;

  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

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
  public void loadState(@NotNull Element element) {
    if (myFirstLoad) {
      myModel.readExternal(element);
    }
    else {
      LibraryModel model = new LibraryModel(myModel);
      WriteAction.run(() -> {
        model.readExternal(element);
        model.commit();
      });
    }

    myFirstLoad = false;
  }

  public long getStateModificationCount() {
    return myModificationTracker.getModificationCount();
  }

  @Override
  public Library @NotNull [] getLibraries() {
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

  void fireLibraryRenamed(@NotNull LibraryImpl library, String oldName) {
    incrementModificationCount();
    myDispatcher.getMulticaster().afterLibraryRenamed(library, oldName);
  }

  private void incrementModificationCount() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("library");
    }
    myModificationTracker.incModificationCount();
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
      return;
    }

    incrementModificationCount();
    Set<Library> removedLibraries = model.myRemovedLibraries;
    for (Library library : removedLibraries) {
      fireBeforeLibraryRemoved(library);
    }

    myModel.applyChanges(model);
    for (Library library : removedLibraries) {
      Disposer.dispose(library);
      fireAfterLibraryRemoved(library);
    }
    for (Library library : model.myAddedLibraries) {
      fireLibraryAdded(library);
    }
  }

  private void fireAfterLibraryRemoved(@NotNull Library library) {
    myDispatcher.getMulticaster().afterLibraryRemoved(library);
  }

  public void readExternal(final Element element) throws InvalidDataException {
    myModel = new LibraryModel();
    myModel.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    myModel.writeExternal(element);
  }

  final class LibraryModel implements ModifiableModel, JDOMExternalizable, Listener, Disposable {
    private final List<Library> myLibraries = new ArrayList<>();
    private final Set<Library> myAddedLibraries = new ReferenceOpenHashSet<>();
    private final Set<Library> myRemovedLibraries = new ReferenceOpenHashSet<>();
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
      myAddedLibraries.clear();
      myRemovedLibraries.clear();
      Disposer.dispose(this);
      myWritable = false;
    }

    @Override
    public void dispose() {
      myDispatcher.removeListener(this);
      for (Library library : myAddedLibraries) {
        Disposer.dispose(library);
      }
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
    public Library @NotNull [] getLibraries() {
      return myLibraries.toArray(Library.EMPTY_ARRAY);
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
    public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> kind) {
      return createLibrary(name, kind, null);
    }

    @NotNull
    @Override
    public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> kind, @Nullable ProjectModelExternalSource externalSource) {
      assertWritable();
      final LibraryImpl library = new LibraryImpl(name, kind, LibraryTableBase.this, null, externalSource);
      myLibraries.add(library);
      if (myWritable) {
        //myAddedLibraries is used only when the model is committed, so let's not populate it in the main model to save memory
        myAddedLibraries.add(library);
      }
      myLibraryByNameCache = null;
      return library;
    }

    @Override
    public void removeLibrary(@NotNull Library library) {
      incrementModificationCount();

      assertWritable();
      myLibraries.remove(library);
      if (myWritable) {
        //myRemovedLibraries are used only when the model is committed, so let's not populate it in the main model to save memory
        if (!myAddedLibraries.remove(library)) {
          myRemovedLibraries.add(library);
        }
        else {
          Disposer.dispose(library);
        }
      }
      myLibraryByNameCache = null;
    }

    @Override
    public boolean isChanged() {
      if (!myWritable) return false;
      return !myAddedLibraries.isEmpty() || !myRemovedLibraries.isEmpty();
    }

    @Override
    public void readExternal(Element element) {
      if (myWritable) {
        myRemovedLibraries.addAll(myLibraries);
      }
      myLibraries.clear();

      final List<Element> libraryElements = element.getChildren(JpsLibraryTableSerializer.LIBRARY_TAG);
      for (Element libraryElement : libraryElements) {
        final LibraryImpl library = new LibraryImpl(LibraryTableBase.this, libraryElement, null);
        if (library.getName() != null) {
          myLibraries.add(library);
          if (myWritable) {
            myAddedLibraries.add(library);
          }
        }
        else {
          Disposer.dispose(library);
        }
      }
      if (!myLibraries.isEmpty()) {
        /* this is a temporary workaround until IDEA-296918 is implemented: we need to invoke 'fireLibraryAdded' under write action,
           but 'readExternal' may be called from a background thread (IDEA-272575) */
        ThrowableRunnable<RuntimeException> fireEvents = () -> {
          for (Library library : myLibraries) {
            fireLibraryAdded(library);
          }
        };
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
          fireEvents.run();
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(fireEvents);
          });
        }
      }
      myLibraryByNameCache = null;
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library, @Nullable String oldName) {
      myLibraryByNameCache = null;
    }

    @Override
    public void writeExternal(Element element) {
      final List<Library> libraries = ContainerUtil.sorted(ContainerUtil.findAll(myLibraries, library -> !((LibraryEx)library).isDisposed()),
      // todo: do not sort if project is directory-based
      (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));

      for (final Library library : libraries) {
        if (library.getName() != null) {
          library.writeExternal(element);
        }
      }
    }

    private void applyChanges(LibraryModel model) {
      myLibraries.removeAll(model.myRemovedLibraries);
      myLibraries.addAll(model.myAddedLibraries);
      myLibraryByNameCache = null;
    }
  }
}
