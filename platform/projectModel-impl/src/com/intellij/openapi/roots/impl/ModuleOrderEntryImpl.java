/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 * @author dsl
 */
public class ModuleOrderEntryImpl extends OrderEntryBaseImpl implements ModuleOrderEntry, WritableOrderEntry, ClonableOrderEntry {
  @NonNls public static final String ENTRY_TYPE = JpsModuleRootModelSerializer.MODULE_TYPE;
  @NonNls public static final String MODULE_NAME_ATTR = JpsModuleRootModelSerializer.MODULE_NAME_ATTRIBUTE;
  @NonNls private static final String EXPORTED_ATTR = JpsJavaModelSerializerExtension.EXPORTED_ATTRIBUTE;
  @NonNls private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";

  private final ModulePointer myModulePointer;
  private boolean myExported;
  @NotNull private DependencyScope myScope;
  private boolean myProductionOnTestDependency;

  ModuleOrderEntryImpl(@NotNull Module module, @NotNull RootModelImpl rootModel) {
    super(rootModel);
    myModulePointer = ModulePointerManager.getInstance(module.getProject()).create(module);
    myScope = DependencyScope.COMPILE;
  }

  ModuleOrderEntryImpl(@NotNull String moduleName, @NotNull RootModelImpl rootModel) {
    super(rootModel);
    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(moduleName);
    myScope = DependencyScope.COMPILE;
  }

  ModuleOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel) throws InvalidDataException {
    super(rootModel);
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    final String moduleName = element.getAttributeValue(MODULE_NAME_ATTR);
    if (moduleName == null) {
      throw new InvalidDataException();
    }

    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(moduleName);
    myScope = DependencyScope.readExternal(element);
    myProductionOnTestDependency = element.getAttributeValue(PRODUCTION_ON_TEST_ATTRIBUTE) != null;
  }

  private ModuleOrderEntryImpl(@NotNull ModuleOrderEntryImpl that, @NotNull RootModelImpl rootModel) {
    super(rootModel);
    myModulePointer = ModulePointerManager.getInstance(rootModel.getProject()).create(that.myModulePointer.getModuleName());
    myExported = that.myExported;
    myProductionOnTestDependency = that.myProductionOnTestDependency;
    myScope = that.myScope;
  }

  @Override
  @NotNull
  public Module getOwnerModule() {
    return getRootModel().getModule();
  }

  public boolean isProductionOnTestDependency() {
    return myProductionOnTestDependency;
  }

  public void setProductionOnTestDependency(boolean productionOnTestDependency) {
    myProductionOnTestDependency = productionOnTestDependency;
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    final OrderRootsEnumerator enumerator = getEnumerator(type);
    return enumerator != null ? enumerator.getRoots() : VirtualFile.EMPTY_ARRAY;
  }

  @Nullable
  private OrderRootsEnumerator getEnumerator(@NotNull OrderRootType type) {
    final Module module = myModulePointer.getModule();
    if (module == null) return null;

    return ModuleRootManagerImpl.getCachingEnumeratorForType(type, module);
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType rootType) {
    final OrderRootsEnumerator enumerator = getEnumerator(rootType);
    return enumerator != null ? enumerator.getUrls() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public boolean isValid() {
    return !isDisposed() && getModule() != null;
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleOrderEntry(this, initialValue);
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return getModuleName();
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  @Nullable
  public Module getModule() {
    return getRootModel().getConfigurationAccessor().getModule(myModulePointer.getModule(), myModulePointer.getModuleName());
  }

  @Override
  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    element.setAttribute(MODULE_NAME_ATTR, getModuleName());
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    if (myProductionOnTestDependency) {
      element.setAttribute(PRODUCTION_ON_TEST_ATTRIBUTE, "");
    }
    rootElement.addContent(element);
  }

  @Override
  public String getModuleName() {
    return myModulePointer.getModuleName();
  }

  @NotNull
  @Override
  public OrderEntry cloneEntry(@NotNull RootModelImpl rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new ModuleOrderEntryImpl(this, rootModel);
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
