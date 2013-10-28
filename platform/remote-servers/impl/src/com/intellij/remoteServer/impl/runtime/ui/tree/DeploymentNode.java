package com.intellij.remoteServer.impl.runtime.ui.tree;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface DeploymentNode {
  @NotNull
  ServerNode getServerNode();

  boolean isUndeployActionEnabled();
  void undeploy();

  boolean isEditConfigurationActionEnabled();
  void editConfiguration();
}
