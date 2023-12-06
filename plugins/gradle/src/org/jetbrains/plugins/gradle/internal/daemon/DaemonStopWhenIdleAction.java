// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonStopClient;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.ArrayList;
import java.util.List;

public class DaemonStopWhenIdleAction extends AbstractDaemonStopAction {
  public DaemonStopWhenIdleAction(String serviceDirectoryPath) {
    super(serviceDirectoryPath);
  }

  @Override
  protected Class<? extends Command> getCommandClass() {
    return StopWhenIdle.class;
  }

  @Override
  protected void stopAll(DaemonStopClient stopClient, ServiceRegistry daemonServices) {
    DaemonRegistry daemonRegistry = daemonServices.get(DaemonRegistry.class);
    List<DaemonConnectDetails> daemonInfos = new ArrayList<>(daemonRegistry.getAll());
    stopClient.gracefulStop(daemonInfos);
  }
}
