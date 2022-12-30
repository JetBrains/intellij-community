// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.NotNull;

public final class DeployToServerConfigurationTypesRegistrar {

  private DeployToServerConfigurationTypesRegistrar() { }

  /**
   * @deprecated Please use {@link DeploymentConfigurationTypesManagerImpl#getConfigurationType(ServerType)} directly.
   */
  @Deprecated
  public static @NotNull DeployToServerConfigurationType<?> getDeployConfigurationType(@NotNull ServerType<?> serverType) {
    return DeploymentConfigurationTypesManagerImpl.getImplementationInstance().getConfigurationType(serverType);
  }
}
