// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class RemoteSdkFactoryImpl<T extends RemoteSdkAdditionalData> implements RemoteSdkFactory<T> {
  private static final Logger LOG = Logger.getInstance(RemoteSdkFactoryImpl.class);
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

    final Sdk sdk = createSdk(existingSdks, sdkType, data, name);

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setVersionString(sdkVersion);

    var modifiableAdditionalData = sdkModificator.getSdkAdditionalData();
    if (!(modifiableAdditionalData instanceof RemoteSdkAdditionalData remoteSdkAdditionalData)) {
      LOG.error("Expected remote additional data, got " + modifiableAdditionalData + " in " + sdk);
      throw new RemoteSdkException("Internal error");
    }
    remoteSdkAdditionalData.setValid(true);

    Application application = ApplicationManager.getApplication();
    Runnable runnable = () -> sdkModificator.commitChanges();
    if (application.isDispatchThread()) {
      application.runWriteAction(runnable);
    } else {
      application.invokeAndWait(() -> application.runWriteAction(runnable));
    }

    return sdk;
  }

  @Override
  public String generateSdkHomePath(@NotNull T data) {
    return data.getSdkId();
  }

  protected abstract @NotNull SdkType getSdkType(@NotNull T data);

  protected abstract @NotNull String getSdkName(@NotNull T data, @Nullable String version) throws RemoteSdkException;

  protected abstract @Nullable String getSdkVersion(Project project, @NotNull T data) throws RemoteSdkException;

  @Override
  public @NotNull Sdk createUnfinished(T data, Collection<Sdk> existingSdks) {
    final String name = getDefaultUnfinishedName();

    final SdkType sdkType = getSdkType(data);

    final Sdk sdk = createSdk(existingSdks, sdkType, data, name);

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
  protected abstract @NotNull Sdk createSdk(@NotNull Collection<Sdk> existingSdks,
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
   * @see #getDefaultUnfinishedName()
   */
  public static @NotNull @Nls String getDefaultUnfinishedInterpreterName(@NotNull String sdkName) {
    return IdeBundle.message("interpreter.default.name", sdkName);
  }
}
