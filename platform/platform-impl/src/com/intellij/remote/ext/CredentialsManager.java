// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.OutdatedCredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import kotlin.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public abstract class CredentialsManager {
  private final static String WSL_PATH_TO_REMOVE = "wsl://";

  public static CredentialsManager getInstance() {
    return ApplicationManager.getApplication().getService(CredentialsManager.class);
  }

  public abstract List<CredentialsType<?>> getAllTypes();

  /**
   * Change old (wsl://) prefix to the new one (\\wsl$\)
   *
   * @deprecated remove after everyone migrates to the new prefix
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  public static void fixWslPrefix(@NotNull Sdk sdk) {
    if (sdk instanceof ProjectJdkImpl) {
      var path = sdk.getHomePath();
      if (path != null && path.startsWith(WSL_PATH_TO_REMOVE)) {
        ((ProjectJdkImpl)sdk).setHomePath(WSLDistribution.UNC_PREFIX + path.substring(WSL_PATH_TO_REMOVE.length()));
      }
    }
  }

  public abstract void loadCredentials(String interpreterPath,
                                       @Nullable Element element,
                                       RemoteSdkAdditionalData data);

  public static void updateOutdatedSdk(@NotNull RemoteSdkAdditionalData<?> data, @Nullable Project project) {
    if (!(data.getRemoteConnectionType() instanceof OutdatedCredentialsType)) {
      return;
    }
    //noinspection unchecked
    Pair<CredentialsType<Object>, Object> pair = ((OutdatedCredentialsType)data.getRemoteConnectionType())
      .transformToNewerType(data.connectionCredentials().getCredentials(), project);
    data.setCredentials(pair.getFirst().getCredentialsKey(), pair.getSecond());
  }

  public static void recogniseCredentialType(@NotNull Stream<? extends SdkAdditionalData> additionalData,
                                             @NotNull CredentialsType<?> credentialsType) {
    additionalData.forEach(data -> recogniseCredentialType(data, credentialsType));
  }

  private static void recogniseCredentialType(@Nullable SdkAdditionalData additionalData, @NotNull CredentialsType credentialsType) {
    if (!(additionalData instanceof RemoteSdkAdditionalData)) return;
    RemoteSdkAdditionalData<?> data = (RemoteSdkAdditionalData<?>)additionalData;
    if (data.getRemoteConnectionType() != CredentialsType.UNKNOWN) return;

    String credentialsId = data.connectionCredentials().getId();
    if (!credentialsType.hasPrefix(credentialsId)) return;

    Element root = new Element("root");
    data.connectionCredentials().save(root);

    Object credentials = credentialsType.createCredentials();
    credentialsType.getHandler(credentials).load(root);
    data.setCredentials(credentialsType.getCredentialsKey(), credentials);
  }

  public static void forgetCredentialType(@NotNull Stream<? extends SdkAdditionalData> additionalData,
                                          @NotNull CredentialsType<?> credentialsType) {
    additionalData.forEach(data -> forgetCredentialType(data, credentialsType));
  }

  private static void forgetCredentialType(@Nullable SdkAdditionalData additionalData, @NotNull CredentialsType<?> credentialsType) {
    if (!(additionalData instanceof RemoteSdkAdditionalData)) return;
    RemoteSdkAdditionalData<?> data = (RemoteSdkAdditionalData<?>)additionalData;
    if (data.getRemoteConnectionType() != credentialsType) return;
    Element root = new Element("root");
    data.connectionCredentials().save(root);

    UnknownCredentialsHolder unknownCredentials = CredentialsType.UNKNOWN.createCredentials();
    unknownCredentials.setSdkId(data.getSdkId());
    CredentialsType.UNKNOWN.getHandler(unknownCredentials).load(root);
    data.setCredentials(CredentialsType.UNKNOWN.getCredentialsKey(), unknownCredentials);
  }
}
