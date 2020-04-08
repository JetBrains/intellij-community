// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.util.ArrayList;
import java.util.List;

final class ModuleSourceOrderEntryImpl extends OrderEntryBaseImpl implements ModuleSourceOrderEntry, WritableOrderEntry, ClonableOrderEntry {
  @NonNls static final String ENTRY_TYPE = JpsModuleRootModelSerializer.SOURCE_FOLDER_TYPE;
  @NonNls private static final String ATTRIBUTE_FOR_TESTS = "forTests";

  ModuleSourceOrderEntryImpl(@NotNull RootModelImpl rootModel) {
    super(rootModel);
  }

  ModuleSourceOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    if (!element.getName().equals(JpsModuleRootModelSerializer.ORDER_ENTRY_TAG)) {
      throw new InvalidDataException();
    }
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE, ENTRY_TYPE);
    element.setAttribute(ATTRIBUTE_FOR_TESTS, Boolean.FALSE.toString()); // compatibility with prev builds
    rootElement.addContent(element);
  }

  @Override
  public boolean isValid() {
    return !isDisposed();
  }

  @Override
  @NotNull
  public Module getOwnerModule() {
    return getRootModel().getModule();
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleSourceOrderEntry(this, initialValue);
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectModelBundle.message("project.root.module.source");
  }


  @Override
  public VirtualFile @NotNull [] getFiles(@NotNull OrderRootType type) {
    if (OrderRootType.SOURCES.equals(type)) {
      return getRootModel().getSourceRoots();
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public String @NotNull [] getUrls(@NotNull OrderRootType type) {
    if (OrderRootType.SOURCES.equals(type)) {
      List<String> result = new ArrayList<>();
      for (ContentEntry contentEntry : getRootModel().getContentEntries()) {
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          result.add(sourceFolder.getUrl());
        }
      }
      return ArrayUtilRt.toStringArray(result);
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new ModuleSourceOrderEntryImpl((RootModelImpl)rootModel);
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }
}
