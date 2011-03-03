/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CheckForUpdateAction extends AnAction implements DumbAware {

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    actionPerformed(project, true, null);
  }

  public static void actionPerformed(Project project, final boolean enableLink, final @Nullable UpdateSettingsConfigurable  settingsConfigurable) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking for updates", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final CheckForUpdateResult result = UpdateChecker.checkForUpdates();

        final List<PluginDownloader> updatedPlugins = UpdateChecker.updatePlugins(true, settingsConfigurable);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (result.getState() == UpdateStrategy.State.CONNECTION_ERROR) {
              UpdateChecker.showConnectionErrorDialog();
              return;
            }

            if (result.hasNewBuildInSelectedChannel()) {
              //information about new channel could be there
              UpdateSettings.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
              UpdateChecker.showUpdateInfoDialog(enableLink, result.getUpdatedChannel(), updatedPlugins);
            }
            else {
              //information about new channel could be there
              UpdateChecker.showNoUpdatesDialog(enableLink, updatedPlugins, true);
            }
          }
        });
      }
    });
  }
}
