package com.intellij.remoteServer.impl.runtime.ui.tree;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface DeploymentNode extends ServersTreeNode {

  @NotNull
  ServerNode getServerNode();

  boolean isDeployActionVisible();

  boolean isDeployActionEnabled();

  void deploy();

  boolean isUndeployActionEnabled();

  void undeploy();

  boolean isDebugActionVisible();

  void deployWithDebug();

  boolean isDeployed();

  String getDeploymentName();
}
