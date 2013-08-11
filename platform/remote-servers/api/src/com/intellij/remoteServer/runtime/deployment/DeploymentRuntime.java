package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DeploymentRuntime {
  public boolean isUndeploySupported() {
    return false;
  }

  public void undeploy(@NotNull UndeploymentTaskCallback callback) {
  }

  public interface UndeploymentTaskCallback extends RemoteOperationCallback {
    void succeeded();
  }
}
