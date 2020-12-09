// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.NotNull;

import java.net.UnknownHostException;

final class PluginsAdvertiserStartupActivity implements StartupActivity.Background {
  private final Object myListRefreshLock = new Object();
  private boolean myListRefreshed = false;

  @Override
  public void runActivity(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || !UpdateSettings.getInstance().isCheckNeeded()) {
      return;
    }

    synchronized (myListRefreshLock) {
      if (!myListRefreshed) {
        myListRefreshed = true;
        PluginsAdvertiser.ensureDeleted();
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        PluginAdvertiserService pluginAdvertiserService = app.getService(PluginAdvertiserService.class);
        pluginAdvertiserService.run(project);
      }
      catch (UnknownHostException e) {
        PluginsAdvertiser.LOG.warn("Host name could not be resolved: " + e.getMessage());
      }
      catch (Exception e) {
        PluginsAdvertiser.LOG.info(e);
      }
    });
  }
}
