/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public abstract class DaemonAction {
  private final String myServiceDirectoryPath;

  public DaemonAction(String serviceDirectoryPath) {
    myServiceDirectoryPath = serviceDirectoryPath;
  }

  protected ServiceRegistry getDaemonServices(DaemonClientFactory daemonClientFactory) {
    BuildLayoutParameters layout = new BuildLayoutParameters();
    if (myServiceDirectoryPath != null && !myServiceDirectoryPath.isEmpty()) {
      layout.setGradleUserHomeDir(new File(myServiceDirectoryPath));
    }
    DaemonParameters daemonParameters = new DaemonParameters(layout);
    return daemonClientFactory.createStopDaemonServices(new OutputEventListener() {
      @Override
      public void onOutput(OutputEvent event) { }
    }, daemonParameters);
  }
}
