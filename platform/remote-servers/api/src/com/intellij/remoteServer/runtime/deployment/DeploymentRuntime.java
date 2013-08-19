package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class DeploymentRuntime {
  public boolean isUndeploySupported() {
    return true;
  }

  public abstract void undeploy(@NotNull UndeploymentTaskCallback callback);

  public interface UndeploymentTaskCallback extends RemoteOperationCallback {
    void succeeded();
  }
}
