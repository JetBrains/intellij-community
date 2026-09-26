// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.util;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import org.jetbrains.annotations.NotNull;

public interface CloudDeploymentNameProvider {

  @NotNull
  @NlsSafe
  String getDeploymentName(@NotNull DeploymentSource deploymentSource);

  CloudDeploymentNameProvider DEFAULT_NAME_PROVIDER = new CloudDeploymentNameProvider() {

    @Override
    public @NotNull String getDeploymentName(@NotNull DeploymentSource deploymentSource) {
      return StringUtil.toLowerCase(FileUtil.sanitizeFileName(deploymentSource.getPresentableName()));
    }
  };
}
