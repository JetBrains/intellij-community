// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    @NotNull
    @Override
    public String getDeploymentName(@NotNull DeploymentSource deploymentSource) {
      return StringUtil.toLowerCase(FileUtil.sanitizeFileName(deploymentSource.getPresentableName()));
    }
  };
}
