// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class RemoteSdkFactoryImpl<T extends RemoteSdkAdditionalData> implements RemoteSdkFactory<T> {
  @Override
  public Sdk createRemoteSdk(@Nullable Project project, @NotNull T data, @Nullable String sdkName, Collection<Sdk> existingSdks)
    throws RemoteSdkException {
    final String sdkVersion = getSdkVersion(project, data);

    final String name;
    if (StringUtil.isNotEmpty(sdkName)) {
      name = sdkName;
    }
    else {
      name = getSdkName(data, sdkVersion);
    }

    final SdkType sdkType = getSdkType(data);

    final ProjectJdkImpl sdk = createSdk(existingSdks, sdkType, data, name);

    sdk.setVersionString(sdkVersion);

    data.setValid(true);

    return sdk;
  }

  @Override
  public String generateSdkHomePath(@NotNull T data) {
    return data.getSdkId();
  }

  @NotNull
  protected abstract SdkType getSdkType(@NotNull T data);

  @NotNull
  protected abstract String getSdkName(@NotNull T data, @Nullable String version) throws RemoteSdkException;

  @Nullable
  protected abstract String getSdkVersion(Project project, @NotNull T data) throws RemoteSdkException;

  @Override
  @NotNull
  public Sdk createUnfinished(T data, Collection<Sdk> existingSdks) {
    final String name = getDefaultUnfinishedName();

    final SdkType sdkType = getSdkType(data);

    final ProjectJdkImpl sdk = createSdk(existingSdks, sdkType, data, name);

    data.setValid(false);

    return sdk;
  }

  /**
   * Creates new SDK.
   * <p>
   * Note that this method is introduced because of the unavailability of
   * {@code SdkConfigurationUtil.createSdk()}.
   *
   * @param existingSdks the existing SDKs
   * @param sdkType      the type of SDK
   * @param data         the additional data of SDK
   * @param sdkName      the name of SDK
   * @return the SDK with the corresponding data
   */
  @NotNull
  protected abstract ProjectJdkImpl createSdk(@NotNull Collection<Sdk> existingSdks,
                                              @NotNull SdkType sdkType,
                                              @NotNull T data,
                                              @Nullable String sdkName);

  /**
   * Returns default name for "unfinished" SDK.
   * <p>
   * "Unfinished" SDK is an SDK that has not yet been introspected or IDE
   * failed to introspect it.
   *
   * @return default name for "unfinished" SDK
   */
  @Override
  public abstract String getDefaultUnfinishedName();


  @Override
  public boolean canSaveUnfinished() {
    return false;
  }

  /**
   * Returns a name for "unfinished" SDK that is related to dynamically
   * interpreted language.
   *
   * @param sdkName
   * @return
   * @see {@link #getDefaultUnfinishedName()}
   */
  @NotNull
  @Nls
  public static String getDefaultUnfinishedInterpreterName(@NotNull String sdkName) {
    return IdeBundle.message("interpreter.default.name", sdkName);
  }
}
