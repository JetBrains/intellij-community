// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime.deployment.debug;

import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this class if a server supports deployment in debug mode. When an user starts a deployment run configuration using 'Debug' button
 * the following happens:
 * <ul>
 *  <li>deployment process is started as usual by calling {@link com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance#deploy deploy}
 *  method; you can check whether deployment is started in debug mode or not by using {@link com.intellij.remoteServer.runtime.deployment.DeploymentTask#isDebugMode() task.isDebugMode()} method</li>
 *  <li>when deployment is finished successfully {@link #getConnectionData} method
 *  is called to retrieve information necessary for debugger from the deployed instance</li>
 *  <li>{@link DebuggerLauncher} is used to start debugging</li>
 * </ul>
 *
 * @see com.intellij.remoteServer.ServerType#createDebugConnector()
 * @see com.intellij.remoteServer.runtime.deployment.DeploymentTask#isDebugMode()
 */
public abstract class DebugConnector<D extends DebugConnectionData, R extends DeploymentRuntime> {
  /**
   * @see JavaDebuggerLauncher#getInstance()
   */
  public abstract @NotNull DebuggerLauncher<D> getLauncher();

  public abstract @NotNull D getConnectionData(@NotNull R runtime) throws DebugConnectionDataNotAvailableException;
}
