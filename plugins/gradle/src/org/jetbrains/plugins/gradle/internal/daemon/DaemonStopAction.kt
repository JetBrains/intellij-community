// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.Stop;

/**
 * @author Vladislav.Soroka
 */
public class DaemonStopAction extends AbstractDaemonStopAction {
  public DaemonStopAction(String serviceDirectoryPath) {
    super(serviceDirectoryPath);
  }

  @Override
  protected Class<? extends Command> getCommandClass() {
    return Stop.class;
  }

  @Override
  protected void stopAll(DaemonStopClient stopClient, ServiceRegistry daemonServices) {
    stopClient.stop();
  }
}
