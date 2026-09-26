// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationBase;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;

public class CloudDeploymentNameConfiguration<Self extends CloudDeploymentNameConfiguration<Self>>
  extends DeploymentConfigurationBase<Self> implements DeploymentNameConfiguration {

  private String myDeploymentName = "";
  private boolean myDefaultDeploymentName = true;

  @Override
  public boolean isDefaultDeploymentName() {
    return myDefaultDeploymentName;
  }

  @Override
  public void setDefaultDeploymentName(boolean defaultDeploymentName) {
    myDefaultDeploymentName = defaultDeploymentName;
  }

  @Override
  public String getDeploymentName() {
    return myDeploymentName;
  }

  @Override
  public void setDeploymentName(String deploymentName) {
    myDeploymentName = deploymentName;
  }

  public String getDeploymentSourceName(DeploymentSource deploymentSource) {
    return isDefaultDeploymentName() ? getDefaultDeploymentSourceName(deploymentSource) : getDeploymentName();
  }

  protected String getDefaultDeploymentSourceName(DeploymentSource deploymentSource) {
    return CloudDeploymentNameProvider.DEFAULT_NAME_PROVIDER.getDeploymentName(deploymentSource);
  }
}
