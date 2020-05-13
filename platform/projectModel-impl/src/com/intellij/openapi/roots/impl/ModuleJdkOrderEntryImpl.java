// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

final class ModuleJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements WritableOrderEntry,
                                                                           ClonableOrderEntry,
                                                                           ModuleJdkOrderEntry,
                                                                           ProjectJdkTable.Listener {
  @NonNls public static final String ENTRY_TYPE = JpsModuleRootModelSerializer.JDK_TYPE;
  @NonNls private static final String JDK_NAME_ATTR = JpsModuleRootModelSerializer.JDK_NAME_ATTRIBUTE;
  @NonNls private static final String JDK_TYPE_ATTR = JpsModuleRootModelSerializer.JDK_TYPE_ATTRIBUTE;

  @Nullable private Sdk myJdk;
  @Nullable private String myJdkName;
  private String myJdkType;

  ModuleJdkOrderEntryImpl(@NotNull Sdk projectJdk, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    init(projectJdk, null, null);
  }

  ModuleJdkOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    if (!element.getName().equals(JpsModuleRootModelSerializer.ORDER_ENTRY_TAG)) {
      throw new InvalidDataException();
    }
    final Attribute jdkNameAttribute = element.getAttribute(JDK_NAME_ATTR);
    if (jdkNameAttribute == null) {
      throw new InvalidDataException();
    }

    final String jdkName = jdkNameAttribute.getValue();
    final String jdkType = element.getAttributeValue(JDK_TYPE_ATTR);
    final Sdk jdkByName = jdkType == null ? null : findJdk(jdkName, jdkType);
    if (jdkByName == null) {
      init(null, jdkName, jdkType);
    }
    else {
      init(jdkByName, null, null);
    }
  }

  @Nullable
  private static Sdk findJdk(@NotNull String sdkName, @NotNull String sdkType) {
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

  private void init(final Sdk jdk, @Nullable final String jdkName, final String jdkType) {
    myJdk = jdk;
    myJdkName = jdkName;
    myJdkType = jdkType;
    myProjectRootManagerImpl.addJdkTableListener(this, this);
    init();
  }

  private String getJdkType() {
    if (myJdk != null){
      return myJdk.getSdkType().getName();
    }
    return myJdkType;
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
  @Nullable
  public String getJdkTypeName() {
    if (myJdkType != null) return myJdkType;
    Sdk jdk = getJdk();
    if (jdk != null) {
      return jdk.getSdkType().getName();
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
      myJdkName = null;
      myJdkType = null;
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
    if (myJdk == null && jdk.getName().equals(getJdkName())) {
      myJdk = jdk;
      myJdkName = null;
      myJdkType = null;
      updateFromRootProviderAndSubscribe();
    }
  }

  @Override
  public void jdkRemoved(@NotNull Sdk jdk) {
    if (jdk == myJdk) {
      myJdkName = myJdk.getName();
      myJdkType = myJdk.getSdkType().getName();
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
  public OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new ModuleJdkOrderEntryImpl(this, (RootModelImpl)rootModel, ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject()));
  }
}
