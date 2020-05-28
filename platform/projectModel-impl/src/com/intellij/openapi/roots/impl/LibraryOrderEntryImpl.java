// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.util.Objects;

final class LibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance(LibraryOrderEntryImpl.class);
  private Library myLibrary;
  @Nullable private String myLibraryName; // is non-null if myLibrary == null
  private String myLibraryLevel; // is non-null if myLibrary == null
  private boolean myExported;
  @NonNls static final String ENTRY_TYPE = JpsModuleRootModelSerializer.LIBRARY_TYPE;
  @NonNls private static final String NAME_ATTR = JpsModuleRootModelSerializer.NAME_ATTRIBUTE;
  @NonNls private static final String LEVEL_ATTR = JpsModuleRootModelSerializer.LEVEL_ATTRIBUTE;
  private final MyOrderEntryLibraryTableListener myLibraryListener = new MyOrderEntryLibraryTableListener();
  @NonNls private static final String EXPORTED_ATTR = JpsJavaModelSerializerExtension.EXPORTED_ATTRIBUTE;

  LibraryOrderEntryImpl(@NotNull Library library, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    LOG.assertTrue(library.getTable() != null);
    myLibrary = library;
    addListeners();
    init();
  }

  LibraryOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    String level = element.getAttributeValue(LEVEL_ATTR);
    String name = element.getAttributeValue(NAME_ATTR);
    if (name == null) throw new InvalidDataException("Incorrect '" + ENTRY_TYPE + "' entry in '" + rootModel.getModule().getName() + "' module: '" + NAME_ATTR + "' isn't specified");
    if (level == null) throw new InvalidDataException("Incorrect '" + ENTRY_TYPE + "' entry in '" + rootModel.getModule().getName() + "' module: '" + LEVEL_ATTR + "' isn't specified");
    searchForLibrary(name, level);
    addListeners();
    init();
  }

  private LibraryOrderEntryImpl(@NotNull LibraryOrderEntryImpl that, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super (rootModel, projectRootManager);
    if (that.myLibrary == null) {
      myLibraryName = that.myLibraryName;
      myLibraryLevel = that.myLibraryLevel;
    }
    else {
      myLibrary = that.myLibrary;
    }
    myExported = that.myExported;
    myScope = that.myScope;
    addListeners();
    init();
  }

  LibraryOrderEntryImpl(@NotNull String name,
                               @NotNull String level,
                               @NotNull RootModelImpl rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    searchForLibrary(name, level);
    addListeners();
  }

  private void searchForLibrary(@NotNull String name, @NotNull String level) {
    if (myLibrary != null) return;
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, getRootModel().getModule().getProject());
    final Library library = libraryTable != null ? libraryTable.getLibraryByName(name) : null;
    if (library == null) {
      myLibraryName = name;
      myLibraryLevel = level;
      myLibrary = null;
    }
    else {
      myLibraryName = null;
      myLibraryLevel = null;
      myLibrary = library;
    }
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean exported) {
    getRootModel().assertWritable();
    myExported = exported;
  }

  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull DependencyScope scope) {
    getRootModel().assertWritable();
    myScope = scope;
  }

  @Override
  @Nullable
  public Library getLibrary() {
    Library library = getRootModel().getConfigurationAccessor().getLibrary(myLibrary, myLibraryName, myLibraryLevel);
    if (library != null) { //library was not deleted
      return library;
    }
    if (myLibrary != null) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
      myLibrary = null;
    }
    return null;
  }

  @Override
  public boolean isModuleLevel() {
    return false;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getLibraryName();
  }

  @Override
  @Nullable
  protected RootProvider getRootProvider() {
    return myLibrary == null ? null : myLibrary.getRootProvider();
  }

  @Override
  public boolean isValid() {
    if (isDisposed()) {
      return false;
    }
    Library library = getLibrary();
    return library != null && !((LibraryEx)library).isDisposed();
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  @Override
  @NotNull
  public OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    ProjectRootManagerImpl rootManager = ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject());
    return new LibraryOrderEntryImpl(this, (RootModelImpl)rootModel, rootManager);
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String libraryLevel = getLibraryLevel();
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    element.setAttribute(NAME_ATTR, getLibraryName());
    element.setAttribute(LEVEL_ATTR, libraryLevel);
    rootElement.addContent(element);
  }

  @Override
  @NotNull
  public String getLibraryLevel() {
    if (myLibrary == null) {
      return myLibraryLevel;
    }
    LibraryTable table = myLibrary.getTable();
    return table.getTableLevel();
  }

  @Override
  public String getLibraryName() {
    return myLibrary == null ? myLibraryName : myLibrary.getName();
  }

  private void addListeners () {
    final String libraryLevel = getLibraryLevel();
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.addListenerForTable(myLibraryListener, libraryTable);
    }
  }


  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public void dispose() {
    super.dispose();
    final LibraryTable libraryTable =
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(getLibraryLevel(), getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.removeListenerForTable(myLibraryListener, libraryTable);
    }
  }


  private void afterLibraryAdded(@NotNull Library newLibrary) {
    if (myLibrary == null) {
      if (Objects.equals(myLibraryName, newLibrary.getName())) {
        myLibrary = newLibrary;
        myLibraryName = null;
        myLibraryLevel = null;
        updateFromRootProviderAndSubscribe();
      }
    }
  }

  private void beforeLibraryRemoved(@NotNull Library library) {
    if (library == myLibrary) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
      myLibrary = null;
      updateFromRootProviderAndSubscribe();
    }
  }

  private class MyOrderEntryLibraryTableListener implements LibraryTable.Listener {
    MyOrderEntryLibraryTableListener() {
    }

    @Override
    public void afterLibraryAdded(@NotNull Library newLibrary) {
      LibraryOrderEntryImpl.this.afterLibraryAdded(newLibrary);
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library, @Nullable String oldName) {
      afterLibraryAdded(library);
    }

    @Override
    public void beforeLibraryRemoved(@NotNull Library library) {
      LibraryOrderEntryImpl.this.beforeLibraryRemoved(library);
    }
  }
}
