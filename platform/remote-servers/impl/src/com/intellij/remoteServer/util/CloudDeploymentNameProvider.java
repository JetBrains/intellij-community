package com.intellij.remoteServer.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import org.jetbrains.annotations.NotNull;

/**
 * @author michael.golubev
 */
public interface CloudDeploymentNameProvider {

  @NotNull
  String getDeploymentName(@NotNull DeploymentSource deploymentSource);

  CloudDeploymentNameProvider DEFAULT_NAME_PROVIDER = new CloudDeploymentNameProvider() {

    @NotNull
    @Override
    public String getDeploymentName(@NotNull DeploymentSource deploymentSource) {
      return FileUtil.sanitizeFileName(deploymentSource.getPresentableName()).toLowerCase();
    }
  };
}
