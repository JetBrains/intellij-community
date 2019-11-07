// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.ui.TableUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author anna
 */
public final class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialog.class);

  @Nullable private final Project myProject;
  private final PluginDownloader[] myUploadedPlugins;
  private final List<? extends IdeaPluginDescriptor> myAllPlugins;
  private final Set<PluginId> mySkippedPlugins = new HashSet<>();

  private final PluginManagerMain.PluginEnabler.HEADLESS pluginHelper = new PluginManagerMain.PluginEnabler.HEADLESS();

  PluginsAdvertiserDialog(@Nullable Project project, PluginDownloader[] plugins, List<? extends IdeaPluginDescriptor> allPlugins) {
    super(project);
    myProject = project;
    Arrays.sort(plugins, (o1, o2) -> o1.getPluginName().compareToIgnoreCase(o2.getPluginName()));
    myUploadedPlugins = plugins;
    myAllPlugins = allPlugins;
    setTitle("Choose Plugins to Install or Enable");
    init();
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel() {
      @Override
      protected Set<PluginId> getSkippedPlugins() {
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
    Set<PluginDescriptor> pluginsToEnable = new HashSet<>();
    List<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myUploadedPlugins) {
      PluginDescriptor plugin = downloader.getDescriptor();
      if (!mySkippedPlugins.contains(plugin.getPluginId())) {
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(PluginDownloader.createPluginNode(null, downloader));
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return;
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginHelper, nodes);

    Runnable notifyRunnable = () -> {
      if (nodes.stream().anyMatch(o -> PluginManagerCore.getPlugin(o.getPluginId()) == null)) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };
    PluginManager.getInstance().enablePlugins(pluginsToEnable, true);
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes, myAllPlugins, true, notifyRunnable, pluginHelper, null);
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