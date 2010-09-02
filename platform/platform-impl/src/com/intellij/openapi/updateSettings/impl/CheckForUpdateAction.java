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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CheckForUpdateAction extends AnAction implements DumbAware {

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
  }

  public void actionPerformed(AnActionEvent e) {
    actionPerformed(true, null);
  }

  public static void actionPerformed(final boolean enableLink, final @Nullable UpdateSettingsConfigurable settingsConfigurable) {
    try {
      final UpdateChannel newVersion = UpdateChecker.checkForUpdates();
      final List<PluginDownloader> updatedPlugins = UpdateChecker.updatePlugins(true, settingsConfigurable);
      if (newVersion != null) {
        UpdateSettings.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
        UpdateChecker.showUpdateInfoDialog(enableLink, newVersion, updatedPlugins);
      }
      else {
        UpdateChecker.showNoUpdatesDialog(enableLink, updatedPlugins, true);
      }
    }
    catch (ConnectionException e) {
      Messages.showErrorDialog(IdeBundle.message("error.checkforupdates.connection.failed"),
                               IdeBundle.message("title.connection.error"));
    }
  }
}
