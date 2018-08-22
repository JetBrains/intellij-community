// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;

/**
 * @author Vladislav.Soroka
 * @since 2/8/2017
 */
public class GradleCleanupService implements Disposable {
  private static final Logger LOG = Logger.getInstance(GradleCleanupService.class);

  @Override
  public void dispose() {
    if(ApplicationManager.getApplication().isUnitTestMode()) return;
    // do not use DefaultGradleConnector.close() it sends org.gradle.launcher.daemon.protocol.StopWhenIdle message and waits
    try {
      GradleDaemonServices.stopDaemons();
    }
    catch (Exception e) {
      LOG.warn("Failed to stop Gradle daemons during IDE shutdown", e);
    }
  }
}
