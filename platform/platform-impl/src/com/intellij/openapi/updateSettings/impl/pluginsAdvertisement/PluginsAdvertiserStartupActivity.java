// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

final class PluginsAdvertiserStartupActivity implements StartupActivity.Background {
  private final AtomicBoolean listRefreshed = new AtomicBoolean();

  @Override
  public void runActivity(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || !UpdateSettings.getInstance().isPluginsCheckNeeded()) {
      return;
    }

   if (listRefreshed.compareAndSet(false, true)) {
     try {
       PluginsAdvertiser.ensureDeleted();
     }
     catch (IOException ignore) {
       listRefreshed.set(false);
     }
   }

    try {
      app.getService(PluginAdvertiserService.class).run(project);
    }
    catch (UnknownHostException e) {
      PluginsAdvertiser.LOG.warn("Host name could not be resolved: " + e.getMessage());
    }
    catch (Exception e) {
      PluginsAdvertiser.LOG.info(e);
    }
  }
}
