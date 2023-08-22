// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represent a specific instance of an SDK in the IDE configuration. Use {@link ProjectJdkTable} to access configured SDKs, and
 * {@link com.intellij.openapi.projectRoots.SdkType} to define a new type of SDKs.
 * @author Eugene Zhuravlev
 */
@ApiStatus.NonExtendable
public interface Sdk extends UserDataHolder {
  @NotNull SdkTypeId getSdkType();

  @NlsSafe @NotNull String getName();

  @NlsSafe @Nullable String getVersionString();

  @NonNls @Nullable String getHomePath();

  @Nullable VirtualFile getHomeDirectory();

  @NotNull RootProvider getRootProvider();

  @NotNull SdkModificator getSdkModificator();

  @Nullable SdkAdditionalData getSdkAdditionalData();

  @NotNull Object clone() throws CloneNotSupportedException;
}
