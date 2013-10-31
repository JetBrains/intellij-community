package com.intellij.remoteServer.util;

import com.intellij.remoteServer.configuration.deployment.DeploymentConfigurationBase;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */

public class CloudDeploymentContextConfiguration<Self extends CloudDeploymentContextConfiguration<Self>>
  extends DeploymentConfigurationBase<Self> {

  private String myContextRoot = "/";
  private boolean myDefaultContextRoot = true;

  public boolean isDefaultContextRoot() {
    return myDefaultContextRoot;
  }

  public void setDefaultContextRoot(boolean defaultContextRoot) {
    myDefaultContextRoot = defaultContextRoot;
  }

  public String getContextRoot() {
    return myContextRoot;
  }

  public void setContextRoot(String contextRoot) {
    myContextRoot = contextRoot;
  }
}
