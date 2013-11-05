package com.intellij.remoteServer.util;

/**
 * @author michael.golubev
 */
public interface DeploymentNameConfiguration {

  boolean isDefaultDeploymentName();

  String getDeploymentName();

  void setDefaultDeploymentName(boolean defaultContextRoot);

  void setDeploymentName(String contextRoot);
}
