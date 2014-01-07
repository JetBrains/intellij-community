package com.intellij.remoteServer.util;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationBase;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;

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

  public String getDeploymentSourceName(DeploymentSource deploymentSource) {
    return isDefaultDeploymentName() ? getDefaultDeploymentSourceName(deploymentSource) : getDeploymentName();
  }

  protected String getDefaultDeploymentSourceName(DeploymentSource deploymentSource) {
    return CloudDeploymentNameProvider.DEFAULT_NAME_PROVIDER.getDeploymentName(deploymentSource);
  }
}
