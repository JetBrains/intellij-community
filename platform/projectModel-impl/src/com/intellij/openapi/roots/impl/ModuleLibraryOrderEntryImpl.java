// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 * Library entry for module ("in-place") libraries.
 * This class isn't supposed to be used from plugins. Use {@link OrderEntryUtil#isModuleLibraryOrderEntry(OrderEntry)} to check whether an
 * entry corresponds to a module-level library.
 */
@ApiStatus.Internal
public class ModuleLibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance(LibraryOrderEntryImpl.class);
  @NotNull
  private final Library myLibrary;
  @NonNls public static final String ENTRY_TYPE = JpsModuleRootModelSerializer.MODULE_LIBRARY_TYPE;
  private boolean myExported;
  @NonNls private static final String EXPORTED_ATTR = JpsJavaModelSerializerExtension.EXPORTED_ATTRIBUTE;

  //cloning
  private ModuleLibraryOrderEntryImpl(@NotNull Library library, @NotNull RootModelImpl rootModel, boolean isExported, @NotNull DependencyScope scope) {
    super(rootModel, ProjectRootManagerImpl.getInstanceImpl(rootModel.getProject()));
    myLibrary = ((LibraryImpl)library).cloneLibrary(getRootModel());
    doinit();
    myExported = isExported;
    myScope = scope;
  }

  ModuleLibraryOrderEntryImpl(String name, final PersistentLibraryKind kind, @NotNull RootModelImpl rootModel,
                              @NotNull ProjectRootManagerImpl projectRootManager, ProjectModelExternalSource externalSource) {
    super(rootModel, projectRootManager);
    myLibrary = LibraryTableImplUtil.createModuleLevelLibrary(name, kind, getRootModel(), externalSource);
    doinit();
  }

  ModuleLibraryOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    myLibrary = LibraryTableImplUtil.loadLibrary(element, getRootModel());
    doinit();
  }

  private void doinit() {
    Disposer.register(this, myLibrary);
    init();
  }

  @Override
  protected RootProvider getRootProvider() {
    return myLibrary.getRootProvider();
  }

  @Override
  @NotNull
  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  public boolean isModuleLevel() {
    return true;
  }

  @Override
  public String getLibraryName() {
    return myLibrary.getName();
  }

  @Override
  public String getLibraryLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    final String name = myLibrary.getName();
    if (name != null) {
      return name;
    }
    else {
      if (myLibrary instanceof LibraryEx && ((LibraryEx)myLibrary).isDisposed()) {
        return ProjectModelBundle.message("disposed.library.title");
      }

      final String[] urls = myLibrary.getUrls(OrderRootType.CLASSES);
      if (urls.length > 0) {
        String url = urls[0];
        return PathUtil.toPresentableUrl(url);
      }
      else {
        return ProjectModelBundle.message("empty.library.title");
      }
    }
  }

  @Override
  public boolean isValid() {
    return !isDisposed();
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }

  @NotNull
  @Override
  public OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new ModuleLibraryOrderEntryImpl(myLibrary, (RootModelImpl)rootModel, myExported, myScope);
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    myLibrary.writeExternal(element);
    rootElement.addContent(element);
  }


  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean value) {
    getRootModel().assertWritable();
    myExported = value;
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
}
