// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.registry.DaemonRegistry;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class DaemonStatusAction extends DaemonAction {

  public DaemonStatusAction(String serviceDirectoryPath) {
    super(serviceDirectoryPath);
  }

  public List<DaemonState> run(DaemonClientFactory daemonClientFactory) {
    ServiceRegistry daemonServices = getDaemonServices(daemonClientFactory);
    DaemonConnector daemonConnector = daemonServices.get(DaemonConnector.class);
    DaemonRegistry daemonRegistry = daemonServices.get(DaemonRegistry.class);
    IdGenerator<?> idGenerator = daemonServices.get(IdGenerator.class);
    return new ReportDaemonStatusClient(daemonRegistry, daemonConnector, idGenerator).get();
  }
}
