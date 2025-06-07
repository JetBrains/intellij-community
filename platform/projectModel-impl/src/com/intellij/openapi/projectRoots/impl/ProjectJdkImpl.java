// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.workspace.jps.entities.SdkEntity;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class ProjectJdkImpl extends UserDataHolderBase implements SdkBridge, SdkModificator, Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectJdkImpl.class);

  private final SdkBridge delegate;
  private SdkModificator modificator;

  @ApiStatus.Internal
  public ProjectJdkImpl(SdkBridge delegate) {
    this.delegate = delegate;
    // register on VirtualFilePointerManager because we want our virtual pointers to be disposed before VFPM to avoid "pointer leaked" diagnostics fired
    Disposer.register((Disposable)VirtualFilePointerManager.getInstance(), this);
  }

  private ProjectJdkImpl(SdkBridge delegate, SdkModificator modificator) {
    this(delegate);
    this.modificator = modificator;
  }

  public ProjectJdkImpl(@NotNull String name, @NotNull SdkTypeId sdkType) {
    this(name, sdkType, "", null);
  }

  public ProjectJdkImpl(@NotNull String name, @NotNull SdkTypeId sdkType, String homePath, String version) {
    SdkEntity.Builder sdkEntity =
      SdkBridgeImpl.Companion.createEmptySdkEntity(name, sdkType.getName(), homePath, version);
    delegate = new SdkBridgeImpl(sdkEntity);
    // register on VirtualFilePointerManager because we want our virtual pointers to be disposed before VFPM to avoid "pointer leaked" diagnostics fired
    Disposer.register((Disposable)VirtualFilePointerManager.getInstance(), this);
  }

  @Override
  public void dispose() {
    if(delegate instanceof Disposable disposable) {
      Disposer.dispose(disposable);
    }
  }

  @Override
  public @NotNull SdkTypeId getSdkType() {
    return delegate.getSdkType();
  }

  @Override
  public @NotNull String getName() {
    if (modificator != null) {
      return modificator.getName();
    } else {
      return delegate.getName();
    }
  }

  @Override
  public void setName(@NotNull String name) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    } else {
      modificator.setName(name);
    }
  }

  @Override
  public final void setVersionString(@Nullable String versionString) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    } else {
      modificator.setVersionString(versionString);
    }
  }

  @Override
  public String getVersionString() {
    if (modificator != null) {
      return modificator.getVersionString();
    } else {
      return delegate.getVersionString();
    }
  }

  public final void resetVersionString() {
    LOG.error("Function is unsupported for the new implementation of SDK");
  }

  @Override
  public String getHomePath() {
    if (modificator != null) {
      return modificator.getHomePath();
    } else {
      return delegate.getHomePath();
    }
  }

  @Override
  public void setHomePath(String path) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    } else {
      modificator.setHomePath(path);
    }
  }

  @Override
  public VirtualFile getHomeDirectory() {
    return delegate.getHomeDirectory();
  }

  @Override
  public void readExternal(@NotNull Element element) {
    delegate.readExternal(element);
  }

  @Override
  public void readExternal(@NotNull Element element, @NotNull Function<String, SdkTypeId> sdkTypeByNameFunction) throws InvalidDataException {
    delegate.readExternal(element, sdkTypeByNameFunction);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    delegate.writeExternal(element);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public @NotNull ProjectJdkImpl clone() {
    return new ProjectJdkImpl(delegate.clone());
  }

  @Override
  public @NotNull RootProvider getRootProvider() {
    return delegate.getRootProvider();
  }

  @Override
  @ApiStatus.Internal
  public void changeType(@NotNull SdkTypeId newType, @Nullable Element additionalDataElement) {
    delegate.changeType(newType, additionalDataElement);
  }

  // SdkModificator implementation
  @Override
  public @NotNull SdkModificator getSdkModificator() {
    if (modificator != null) {
      LOG.error("Forbidden to call `getSdkModificator` on already modifiable version of SDK");
    }
    var sdkBridge = (SdkBridgeImpl)delegate;
    return new ProjectJdkImpl(delegate, sdkBridge.getSdkModificator(this));
  }

  @Override
  public void commitChanges() {
    if (modificator == null) {
      LOG.error("Forbidden to call `commitChanges` outside of `SdkModificator`");
    }
    ThreadingAssertions.assertWriteAccess();
    modificator.commitChanges();
    SdkAdditionalData sdkAdditionalData = modificator.getSdkAdditionalData();
    if (sdkAdditionalData != null) sdkAdditionalData.markAsCommited();
    modificator = null;
  }

  @Override
  public void applyChangesWithoutWriteAction() {
    modificator.applyChangesWithoutWriteAction();
    modificator = null;
  }

  @Override
  public SdkAdditionalData getSdkAdditionalData() {
    if (modificator != null) {
      return modificator.getSdkAdditionalData();
    } else {
      SdkAdditionalData sdkAdditionalData = delegate.getSdkAdditionalData();
      if (sdkAdditionalData != null) sdkAdditionalData.markAsCommited();
      return sdkAdditionalData;
    }
  }

  @Override
  public void setSdkAdditionalData(SdkAdditionalData data) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    } else {
      modificator.setSdkAdditionalData(data);
    }
  }

  @ApiStatus.Internal
  public SdkBridge getDelegate() {
    return delegate;
  }

  @Override
  public VirtualFile @NotNull [] getRoots(@NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to call `getRoots` outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    return modificator.getRoots(rootType);
  }

  @Override
  public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to call `getUrls` outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    return modificator.getUrls(rootType);
  }

  @Override
  public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.addRoot(root, rootType);
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.addRoot(url, rootType);
  }

  @Override
  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.removeRoot(root, rootType);
  }

  @Override
  public void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.removeRoot(url, rootType);
  }

  @Override
  public void removeRoots(@NotNull OrderRootType rootType) {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.removeRoots(rootType);
  }

  @Override
  public void removeAllRoots() {
    if (modificator == null) {
      LOG.error("Forbidden to mutate SDK outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    modificator.removeAllRoots();
  }

  @Override
  public boolean isWritable() {
    if (modificator == null) {
      LOG.error("Forbidden to call `isWritable` outside of the `SdkModificator`. Please, use `com.intellij.openapi.projectRoots.Sdk.getSdkModificator`");
    }
    return modificator.isWritable();
  }

  @Override
  public String toString() {
    if (modificator != null) {
      return modificator.toString();
    } else {
      return delegate.toString();
    }
  }
}
