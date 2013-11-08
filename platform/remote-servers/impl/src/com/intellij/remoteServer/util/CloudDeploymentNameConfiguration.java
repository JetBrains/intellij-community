package com.intellij.remoteServer.util;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationBase;

/**
 * @author michael.golubev
 */

public class CloudDeploymentNameConfiguration<Self extends CloudDeploymentNameConfiguration<Self>>
  extends DeploymentConfigurationBase<Self> implements DeploymentNameConfiguration {

  private String myDeploymentName = "";
  private boolean myDefaultDeploymentName = true;

  public boolean isDefaultDeploymentName() {
    return myDefaultDeploymentName;
  }

  public void setDefaultDeploymentName(boolean defaultDeploymentName) {
    myDefaultDeploymentName = defaultDeploymentName;
  }

  public String getDeploymentName() {
    return myDeploymentName;
  }

  public void setDeploymentName(String deploymentName) {
    myDeploymentName = deploymentName;
  }
}
