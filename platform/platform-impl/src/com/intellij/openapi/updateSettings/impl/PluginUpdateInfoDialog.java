// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author pti
 */
final class PluginUpdateInfoDialog extends AbstractUpdateDialog {
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
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.updateSettings.impl.PluginUpdateInfoDialog";
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
      String message = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
      new Task.Backgroundable(null, message, true, PerformInBackgroundOption.DEAF) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final List<PluginDownloader> downloaders = UpdateInstaller.downloadPluginUpdates(myUploadedPlugins, indicator);
          if (!downloaders.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              final PluginUpdateResult result = UpdateInstaller.installDownloadedPluginUpdates(downloaders, getContentPanel(), true);
              if (result.getPluginsInstalled().size() > 0) {
                if (result.getRestartRequired()) {
                  PluginManagerMain.notifyPluginsUpdated(null);
                }
                else {
                  String message = notificationText(result);
                  UpdateChecker.getNotificationGroupForUpdateResults()
                    .createNotification(message, NotificationType.INFORMATION, "plugins.updated.without.restart")
                    .notify(myProject);
                }
              }
            });
          }
        }
      }.queue();
    }
  }

  static @NlsContexts.NotificationContent @NotNull String notificationText(@NotNull PluginUpdateResult result) {
    if (result.getPluginsInstalled().size() == 1) {
      IdeaPluginDescriptor installedPlugin = result.getPluginsInstalled().get(0);
      return IdeBundle.message("notification.content.updated.plugin.to.version", installedPlugin.getName(), installedPlugin.getVersion());
    }
    else {
      return IdeBundle.message("notification.content.updated.plugins",
                               StringUtil.join(result.getPluginsInstalled(), plugin -> plugin.getName(), ", "));
    }
  }

  private class PluginUpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myPluginsToUpdateLabel;
    private JPanel myPluginsPanel;
    private JEditorPane myMessageArea;

    PluginUpdateInfoPanel() {
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
      if (!myPlatformUpdate) {
        Set<PluginId> skipped = panel.getSkippedPlugins();
        boolean nothingSelected = myUploadedPlugins.stream().allMatch(plugin -> skipped.contains(plugin.getId()));
        getOKAction().setEnabled(!nothingSelected);
      }
    }
  }
}
