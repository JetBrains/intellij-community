/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 * @author dsl
 */
public class ModuleJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements WritableOrderEntry,
                                                                                  ClonableOrderEntry,
                                                                                  ModuleJdkOrderEntry,
                                                                                  ProjectJdkTable.Listener {
  @NonNls public static final String ENTRY_TYPE = JpsModuleRootModelSerializer.JDK_TYPE;
  @NonNls public static final String JDK_NAME_ATTR = JpsModuleRootModelSerializer.JDK_NAME_ATTRIBUTE;
  @NonNls public static final String JDK_TYPE_ATTR = JpsModuleRootModelSerializer.JDK_TYPE_ATTRIBUTE;

  @Nullable private Sdk myJdk;
  private String myJdkName;
  private String myJdkType;

  ModuleJdkOrderEntryImpl(@NotNull Sdk projectJdk, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    init(projectJdk, null, null);
  }

  ModuleJdkOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
    final Attribute jdkNameAttribute = element.getAttribute(JDK_NAME_ATTR);
    if (jdkNameAttribute == null) {
      throw new InvalidDataException();
    }

    final String jdkName = jdkNameAttribute.getValue();
    final String jdkType = element.getAttributeValue(JDK_TYPE_ATTR);
    final Sdk jdkByName = findJdk(jdkName, jdkType);
    if (jdkByName == null) {
      init(null, jdkName, jdkType);
    }
    else {
      init(jdkByName, null, null);
    }
  }

  @Nullable
  private static Sdk findJdk(final String sdkName, final String sdkType) {
    for (SdkFinder sdkFinder : SdkFinder.EP_NAME.getExtensions()) {
      final Sdk sdk = sdkFinder.findSdk(sdkName, sdkType);
      if (sdk != null) {
        return sdk;
      }
    }
    final ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    return projectJdkTable.findJdk(sdkName, sdkType);
  }


  private ModuleJdkOrderEntryImpl(@NotNull ModuleJdkOrderEntryImpl that, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    init(that.myJdk, that.getJdkName(), that.getJdkType());
  }

  ModuleJdkOrderEntryImpl(final String jdkName,
                          final String jdkType,
                          @NotNull final RootModelImpl rootModel,
                          @NotNull final ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    init(null, jdkName, jdkType);
  }

  private void init(final Sdk jdk, final String jdkName, final String jdkType) {
    myJdk = jdk;
    setJdkName(jdkName);
    setJdkType(jdkType);
    addListener();
    init();
  }

  private String getJdkType() {
    if (myJdk != null){
      return myJdk.getSdkType().getName();
    }
    return myJdkType;
  }

  private void addListener() {
    myProjectRootManagerImpl.addJdkTableListener(this);
  }

  @Override
  protected RootProvider getRootProvider() {
    return myJdk == null ? null : myJdk.getRootProvider();
  }

  @Override
  @Nullable
  public Sdk getJdk() {
    return getRootModel().getConfigurationAccessor().getSdk(myJdk, myJdkName);
  }

  @Override
  @Nullable
  public String getJdkName() {
    if (myJdkName != null) return myJdkName;
    Sdk jdk = getJdk();
    if (jdk != null) {
      return jdk.getName();
    }
    return null;
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }


  @Override
  @NotNull
  public String getPresentableName() {
    return "< " + (myJdk == null ? getJdkName() : myJdk.getName())+ " >";
  }

  @Override
  public boolean isValid() {
    return !isDisposed() && getJdk() != null;
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleJdkOrderEntry(this, initialValue);
  }

  @Override
  public void jdkAdded(@NotNull Sdk jdk) {
    if (myJdk == null && jdk.getName().equals(getJdkName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
    if (myJdk == null && jdk.getName().equals(getJdkName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void jdkRemoved(@NotNull Sdk jdk) {
    if (jdk == myJdk) {
      setJdkName(myJdk.getName());
      setJdkType(myJdk.getSdkType().getName());
      myJdk = null;
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String jdkName = getJdkName();
    if (jdkName != null) {
      element.setAttribute(JDK_NAME_ATTR, jdkName);
    }
    final String jdkType = getJdkType();
    if (jdkType != null) {
      element.setAttribute(JDK_TYPE_ATTR, jdkType);
    }
    rootElement.addContent(element);
  }

  @Override
  @NotNull
  public OrderEntry cloneEntry(@NotNull RootModelImpl rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new ModuleJdkOrderEntryImpl(this, rootModel, ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject()));
  }

  @Override
  public void dispose() {
    super.dispose();
    myProjectRootManagerImpl.removeJdkTableListener(this);
  }

  private void setJdkName(String jdkName) {
    myJdkName = jdkName;
  }

  private void setJdkType(String jdkType) {
    myJdkType = jdkType;
  }
}
