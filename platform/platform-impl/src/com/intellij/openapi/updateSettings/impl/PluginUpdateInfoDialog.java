/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.TableUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author pti
 */
class PluginUpdateInfoDialog extends AbstractUpdateDialog {
  private final Collection<PluginDownloader> myUploadedPlugins;

  public PluginUpdateInfoDialog(Collection<PluginDownloader> uploadedPlugins, boolean enableLink) {
    super(enableLink);
    myUploadedPlugins = uploadedPlugins;
    init();
  }

  protected PluginUpdateInfoDialog(Component parent, @NotNull Collection<PluginDownloader> updatePlugins, boolean enableLink) {
    super(parent, enableLink);
    myUploadedPlugins = updatePlugins;
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new PluginUpdateInfoPanel().myPanel;
  }

  @Override
  protected String getOkButtonText() {
    return IdeBundle.message("update.plugins.update.action");
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    final Ref<Boolean> result = new Ref<Boolean>();
    final Runnable runnable = new Runnable() {
      public void run() {
        UpdateChecker.saveDisabledToUpdatePlugins();
        result.set(UpdateChecker.install(myUploadedPlugins));
      }
    };

    final String progressTitle = "Download plugins...";
    if (downloadModal()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, progressTitle, true, null);
    } else {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, progressTitle, true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runnable.run();
        }

        @Override
        public void onSuccess() {
          final Boolean installed = result.get();
          if (installed != null && installed.booleanValue()) {
            final String pluginName;
            if (myUploadedPlugins.size() == 1) {
              final PluginDownloader firstItem = ContainerUtil.getFirstItem(myUploadedPlugins);
              pluginName = firstItem != null ? firstItem.getPluginName() : null;
            }
            else {
              pluginName = null;
            }
            PluginManagerMain.notifyPluginsWereInstalled(pluginName, null);
          }
        }
      });
    }
  }

  protected boolean downloadModal() {
    return false;
  }

  private class PluginUpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myPluginsToUpdateLabel;
    private JPanel myPluginsPanel;
    private JEditorPane myMessageArea;

    public PluginUpdateInfoPanel() {
      myPluginsToUpdateLabel.setVisible(true);
      myPluginsPanel.setVisible(true);

      final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel();
      foundPluginsPanel.addAll(myUploadedPlugins);
      TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
      foundPluginsPanel.addStateListener(new DetectedPluginsPanel.Listener() {
        @Override
        public void stateChanged() {
          boolean allSkipped = foundPluginsPanel.getSkippedPlugins().size() == myUploadedPlugins.size();
          getOKAction().setEnabled(!allSkipped);
        }
      });
      myPluginsPanel.add(foundPluginsPanel, BorderLayout.CENTER);

      configureMessageArea(myMessageArea);
    }
  }
}
