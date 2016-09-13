/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Set;

/**
 * @author pti
 */
class PluginUpdateInfoDialog extends AbstractUpdateDialog {
  private final Collection<PluginDownloader> myUploadedPlugins;
  private final boolean myPlatformUpdate;

  PluginUpdateInfoDialog(Collection<PluginDownloader> uploadedPlugins, boolean enableLink) {
    super(enableLink);
    myUploadedPlugins = uploadedPlugins;
    myPlatformUpdate = false;
    init();
  }

  /**
   * Used from {@link UpdateInfoDialog} when both platform and plugin updates are available.
   */
  PluginUpdateInfoDialog(@NotNull Collection<PluginDownloader> updatePlugins) {
    super(false);
    myUploadedPlugins = updatePlugins;
    myPlatformUpdate = true;
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new PluginUpdateInfoPanel().myPanel;
  }

  @Override
  protected String getOkButtonText() {
    if (!myPlatformUpdate) {
      return IdeBundle.message("update.plugins.update.action");
    }
    else {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      return IdeBundle.message(canRestart ? "update.restart.plugins.update.action" : "update.shutdown.plugins.update.action");
    }
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    if (!myPlatformUpdate) {
      new Task.Backgroundable(null, IdeBundle.message("progress.downloading.plugins"), true, PerformInBackgroundOption.DEAF) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          UpdateChecker.saveDisabledToUpdatePlugins();
          boolean updated = UpdateInstaller.installPluginUpdates(myUploadedPlugins, indicator);
          if (updated) {
            ApplicationManager.getApplication().invokeLater(() -> PluginManagerMain.notifyPluginsUpdated(null), ModalityState.NON_MODAL);
          }
        }
      }.queue();
    }
  }

  private class PluginUpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myPluginsToUpdateLabel;
    private JPanel myPluginsPanel;
    private JEditorPane myMessageArea;

    public PluginUpdateInfoPanel() {
      myPluginsToUpdateLabel.setVisible(true);
      myPluginsPanel.setVisible(true);

      DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel();
      foundPluginsPanel.addAll(myUploadedPlugins);
      TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
      foundPluginsPanel.addStateListener(() -> updateState(foundPluginsPanel));
      myPluginsPanel.add(foundPluginsPanel, BorderLayout.CENTER);

      configureMessageArea(myMessageArea);

      updateState(foundPluginsPanel);
    }

    private void updateState(DetectedPluginsPanel panel) {
      Set<String> skipped = panel.getSkippedPlugins();
      boolean nothingSelected = myUploadedPlugins.stream().allMatch(plugin -> skipped.contains(plugin.getPluginId()));
      getOKAction().setEnabled(!nothingSelected);
    }
  }
}