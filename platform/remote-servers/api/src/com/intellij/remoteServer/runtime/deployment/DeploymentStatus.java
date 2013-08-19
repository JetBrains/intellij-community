package com.intellij.remoteServer.runtime.deployment;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public enum DeploymentStatus {
  DEPLOYED("Deployed"), NOT_DEPLOYED("Not deployed"), DEPLOYING("Deploying"), UNDEPLOYING("Undeploying");
  private String myPresentableText;

  DeploymentStatus(@NotNull String presentableText) {
    myPresentableText = presentableText;
  }

  public String getPresentableText() {
    return myPresentableText;
  }
}
