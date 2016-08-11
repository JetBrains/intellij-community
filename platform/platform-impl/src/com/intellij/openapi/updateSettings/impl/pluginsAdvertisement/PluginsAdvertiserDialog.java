/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author anna
 */
public class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiserDialog.class.getName());

  @Nullable private final Project myProject;
  private final PluginDownloader[] myUploadedPlugins;
  private final List<PluginId> myAllPlugins;
  private final Set<String> mySkippedPlugins = new HashSet<>();

  private final PluginManagerMain.PluginEnabler.HEADLESS pluginHelper = new PluginManagerMain.PluginEnabler.HEADLESS();

  PluginsAdvertiserDialog(@Nullable Project project, PluginDownloader[] plugins, List<PluginId> allPlugins) {
    super(project);
    myProject = project;
    Arrays.sort(plugins, (o1, o2) -> o1.getPluginName().compareToIgnoreCase(o2.getPluginName()));
    myUploadedPlugins = plugins;
    myAllPlugins = allPlugins;
    setTitle("Choose Plugins to Install or Enable");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel() {
      @Override
      protected Set<String> getSkippedPlugins() {
        return mySkippedPlugins;
      }
    };

    for (PluginDownloader uploadedPlugin : myUploadedPlugins) {
      foundPluginsPanel.add(uploadedPlugin);
    }
    TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
    return foundPluginsPanel;
  }

  @Override
  protected void doOKAction() {
    final Set<String> pluginsToEnable = new HashSet<>();
    final List<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myUploadedPlugins) {
      String pluginId = downloader.getPluginId();
      if (!mySkippedPlugins.contains(pluginId)) {
        pluginsToEnable.add(pluginId);
        if (!pluginHelper.isDisabled(pluginId)) {
          final PluginNode pluginNode = PluginDownloader.createPluginNode(null, downloader);
          if (pluginNode != null) {
            nodes.add(pluginNode);
          }
        }
      }
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginHelper, nodes);

    final Runnable notifyRunnable = () -> PluginManagerMain.notifyPluginsUpdated(myProject);
    for (String pluginId : pluginsToEnable) {
      PluginManagerCore.enablePlugin(pluginId);
    }
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes, myAllPlugins, notifyRunnable, null);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      if (!pluginsToEnable.isEmpty()) {
        notifyRunnable.run();
      }
    }
    super.doOKAction();
  }
}
