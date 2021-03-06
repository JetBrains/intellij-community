// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

final class InheritedJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements InheritedJdkOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  @NonNls public static final String ENTRY_TYPE = JpsModuleRootModelSerializer.INHERITED_JDK_TYPE;
  private final MyProjectJdkListener myListener = new MyProjectJdkListener();

  InheritedJdkOrderEntryImpl(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    myProjectRootManagerImpl.addProjectJdkListener(myListener);
    myProjectRootManagerImpl.addJdkTableListener(new MyJdkTableListener(), this);
    init();
  }

  InheritedJdkOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    this(rootModel, projectRootManager);
    if (!element.getName().equals(JpsModuleRootModelSerializer.ORDER_ENTRY_TAG)) {
      throw new InvalidDataException(element.getName());
    }
  }

  @NotNull
  @Override
  public OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    return new InheritedJdkOrderEntryImpl((RootModelImpl)rootModel, projectRootManager);
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public boolean isValid() {
    return !isDisposed() && getJdk() != null;
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitInheritedJdkOrderEntry(this, initialValue);
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    final Element orderEntryElement = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    rootElement.addContent(orderEntryElement);
  }

  @Override
  @Nullable
  public Sdk getJdk() {
    final Project project = getRootModel().getModule().getProject();
    return getRootModel().getConfigurationAccessor().getProjectSdk(project);
  }

  @Override
  @Nullable
  public String getJdkName() {
    final Project project = getRootModel().getModule().getProject();
    return getRootModel().getConfigurationAccessor().getProjectSdkName(project);
  }

  @Override
  protected RootProvider getRootProvider() {
    final Sdk projectJdk = myProjectRootManagerImpl.getProjectSdk();
    return projectJdk == null ? null : projectJdk.getRootProvider();
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return "< " + getJdkName() + " >";
  }

  @Override
  public void dispose() {
    super.dispose();
    myProjectRootManagerImpl.removeProjectJdkListener(myListener);
  }


  private class MyJdkTableListener implements ProjectJdkTable.Listener {
    @Override
    public void jdkRemoved(@NotNull Sdk jdk) {
      if (jdk.equals(getJdk())) {
        updateFromRootProviderAndSubscribe();
      }
    }

    @Override
    public void jdkAdded(@NotNull Sdk jdk) {
      if (isAffectedByJdk(jdk)) {
        updateFromRootProviderAndSubscribe();
      }
    }

    @Override
    public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
      if (isAffectedByJdk(jdk)) {
        // if current name matches my name
        updateFromRootProviderAndSubscribe();
      }
    }

    private boolean isAffectedByJdk(Sdk jdk) {
      return jdk.getName().equals(getJdkName());
    }
  }

  private class MyProjectJdkListener implements ProjectRootManagerEx.ProjectJdkListener {
    @Override
    public void projectJdkChanged() {
      updateFromRootProviderAndSubscribe();
    }
  }


}
