/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.internal.daemon;

import com.intellij.openapi.util.text.StringUtil;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public abstract class DaemonAction {
  protected ServiceRegistry getDaemonServices(DaemonClientFactory daemonClientFactory) {
    BuildLayoutParameters layout = new BuildLayoutParameters();
    String serviceDirectoryPath = GradleSystemSettings.getInstance().getServiceDirectoryPath();
    if (StringUtil.isNotEmpty(serviceDirectoryPath)) {
      layout.setGradleUserHomeDir(new File(serviceDirectoryPath));
    }
    DaemonParameters daemonParameters = new DaemonParameters(layout);
    return daemonClientFactory.createStopDaemonServices(new OutputEventListener() {
      @Override
      public void onOutput(OutputEvent event) { }
    }, daemonParameters);
  }
}
