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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockSdk implements Sdk, SdkModificator {
  private String myName;
  private String myHomePath;
  @NotNull private String myVersionString;
  private final MultiMap<OrderRootType, VirtualFile> myRoots;
  private final SdkTypeId mySdkType;

  public MockSdk(@NotNull String name,
          @NotNull String homePath,
          @NotNull String versionString,
          @NotNull MultiMap<OrderRootType, VirtualFile> roots,
          @NotNull SdkTypeId sdkType) {
    myName = name;
    myHomePath = homePath;
    myVersionString = versionString;
    myRoots = roots;
    mySdkType = sdkType;
  }

  @NotNull
  @Override
  public SdkTypeId getSdkType() {
    return mySdkType;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getVersionString() {
    return myVersionString;
  }

  @Override
  public String getHomePath() {
    return myHomePath;
  }

  @Nullable
  @Override
  public VirtualFile getHomeDirectory() {
    return StandardFileSystems.local().findFileByPath(myHomePath);
  }

  @Nullable
  @Override
  public SdkAdditionalData getSdkAdditionalData() {
    return null;
  }

  @NotNull
  @Override
  public Sdk clone() {
    return new MockSdk(myName, myHomePath, myVersionString, new MultiMap<>(myRoots), mySdkType) {
      private final UserDataHolder udh = new UserDataHolderBase();
      @NotNull
      @Override
      public SdkModificator getSdkModificator() {
        return this;
      }

      @Nullable
      @Override
      public <T> T getUserData(@NotNull Key<T> key) {
        return udh.getUserData(key);
      }

      @Override
      public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
        udh.putUserData(key, value);
      }
    };
  }

  @NotNull
  @Override
  public SdkModificator getSdkModificator() {
    throwReadOnly();
    return null;
  }

  @NotNull
  public VirtualFile[] getRoots(@NotNull OrderRootType rootType) {
    return myRoots.get(rootType).toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public void setName(String name) {
    myName = name;
  }

  @Override
  public void setHomePath(String path) {
    myHomePath = path;
  }

  @Override
  public void setVersionString(@NotNull String versionString) {
    myVersionString = versionString;
  }

  @Override
  public void setSdkAdditionalData(SdkAdditionalData data) {
    throwReadOnly();
  }

  @Override
  public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    myRoots.putValue(rootType, root);
  }

  @Override
  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    myRoots.remove(rootType, root);
  }

  @Override
  public void removeRoots(@NotNull OrderRootType rootType) {
    myRoots.remove(rootType);
  }

  @Override
  public void removeAllRoots() {
    myRoots.clear();
  }

  @Override
  public void commitChanges() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      WriteAction
        .run(() -> ((ProjectRootManagerEx)ProjectRootManager.getInstance(project)).makeRootsChange(EmptyRunnable.getInstance(), false, true));
    }
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @NotNull
  @Override
  public RootProvider getRootProvider() {
    return new RootProvider() {
      @NotNull
      @Override
      public String[] getUrls(@NotNull OrderRootType rootType) {
        return ContainerUtil.map2Array(getFiles(rootType), String.class, VirtualFile::getUrl);
      }

      @NotNull
      @Override
      public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
        return getRoots(rootType);
      }

      @Override
      public void addRootSetChangedListener(@NotNull RootSetChangedListener listener) { }

      @Override
      public void addRootSetChangedListener(@NotNull RootSetChangedListener listener, @NotNull Disposable parentDisposable) { }

      @Override
      public void removeRootSetChangedListener(@NotNull RootSetChangedListener listener) { }
    };
  }

  private void throwReadOnly() {
    throw new IncorrectOperationException("Can't modify, MockJDK is read-only, consider calling .clone() first");
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    throwReadOnly();
  }

  @Override
  public String toString() {
    return "MockSDK[" + myName + "]";
  }
}
