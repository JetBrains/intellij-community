// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.deployment;

import com.intellij.remoteServer.runtime.RemoteOperationCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeploymentRuntime {
  public boolean isUndeploySupported() {
    return true;
  }

  public abstract void undeploy(@NotNull UndeploymentTaskCallback callback);

  public @Nullable DeploymentRuntime getParent() {
    return null;
  }

  public interface UndeploymentTaskCallback extends RemoteOperationCallback {
    void succeeded();
  }
}
