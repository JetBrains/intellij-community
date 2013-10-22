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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* User: anna
*/
public class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiserDialog.class.getName());

  @Nullable private final Project myProject;
  private final PluginDownloader[] myUploadedPlugins;
  private final List<IdeaPluginDescriptor> myAllPlugins;
  private final HashSet<String> mySkippedPlugins = new HashSet<String>();

  PluginsAdvertiserDialog(@Nullable Project project, PluginDownloader[] plugins, List<IdeaPluginDescriptor> allPlugins) {
    super(project);
    myProject = project;
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
    final Set<IdeaPluginDescriptor> pluginsToEnable = new HashSet<IdeaPluginDescriptor>();
    final List<PluginNode> nodes = new ArrayList<PluginNode>();
    for (PluginDownloader downloader : myUploadedPlugins) {
      if (!mySkippedPlugins.contains(downloader.getPluginId())) {
        final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId(downloader.getPluginId()));
        if (descriptor != null) {
          pluginsToEnable.add(descriptor);
        } else {
          final PluginNode pluginNode = PluginDownloader.createPluginNode(null, downloader);
          if (pluginNode != null) {
            nodes.add(pluginNode);
          }
        }
      }
    }
    final Runnable notifyRunnable = new Runnable() {
      @Override
      public void run() {
        PluginManagerMain.notifyPluginsWereInstalled(null, myProject);
      }
    };
    try {
      PluginManagerMain.downloadPlugins(nodes, myAllPlugins, notifyRunnable, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    for (IdeaPluginDescriptor pluginDescriptor : pluginsToEnable) {
      PluginManagerCore.enablePlugin(pluginDescriptor.getPluginId().getIdString());
    }
    if (nodes.isEmpty()) {
      notifyRunnable.run();
    }
    super.doOKAction();
  }
}
