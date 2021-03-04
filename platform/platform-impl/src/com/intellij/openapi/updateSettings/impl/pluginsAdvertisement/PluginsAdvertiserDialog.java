// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
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
import java.util.function.Function;

/**
 * @author anna
 */
public final class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialog.class);

  private final @Nullable Project myProject;
  private final @NotNull SortedSet<PluginDownloader> myPluginToInstall;
  private final @NotNull List<? extends IdeaPluginDescriptor> myCustomPlugins;
  private final Set<PluginId> mySkippedPlugins = new HashSet<>();

  private final PluginManagerMain.PluginEnabler.HEADLESS pluginHelper = new PluginManagerMain.PluginEnabler.HEADLESS();

  private final @Nullable Function<? super Boolean, Void> myFinishFunction;

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Set<PluginDownloader> pluginsToInstall,
                          @NotNull List<? extends IdeaPluginDescriptor> customPlugins,
                          @Nullable Function<? super Boolean, Void> finishFunction) {
    super(project);
    myProject = project;
    myPluginToInstall = new TreeSet<>(Comparator.comparing(PluginDownloader::getPluginName, String::compareToIgnoreCase));
    myPluginToInstall.addAll(pluginsToInstall);
    myCustomPlugins = customPlugins;
    myFinishFunction = finishFunction;
    setTitle(IdeBundle.message("dialog.title.choose.plugins.to.install.or.enable"));
    init();
  }

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Set<PluginDownloader> pluginsToInstall,
                          @NotNull List<? extends IdeaPluginDescriptor> customPlugins) {
    this(project, pluginsToInstall, customPlugins, null);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    final DetectedPluginsPanel foundPluginsPanel = new DetectedPluginsPanel() {
      @Override
      protected Set<PluginId> getSkippedPlugins() {
        return mySkippedPlugins;
      }
    };
    foundPluginsPanel.addAll(myPluginToInstall);

    TableUtil.ensureSelectionExists(foundPluginsPanel.getEntryTable());
    return foundPluginsPanel;
  }

  @Override
  protected void doOKAction() {
    if (doInstallPlugins()) {
      super.doOKAction();
    }
  }

  public void doInstallPlugins(boolean showDialog) {
    if (showDialog) {
      showAndGet();
    }
    else {
      doInstallPlugins();
    }
  }

  private boolean doInstallPlugins() {
    Set<PluginDescriptor> pluginsToEnable = new HashSet<>();
    List<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myPluginToInstall) {
      PluginDescriptor plugin = downloader.getDescriptor();
      if (!mySkippedPlugins.contains(plugin.getPluginId())) {
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(PluginDownloader.createPluginNode(null, downloader));
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return false;
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginHelper, nodes);

    Runnable notifyRunnable = () -> {
      if (nodes.stream().anyMatch(o -> PluginManagerCore.getPlugin(o.getPluginId()) == null)) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };
    DisabledPluginsState.enablePlugins(pluginsToEnable, true);
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes, myCustomPlugins, true, notifyRunnable, pluginHelper, myFinishFunction);
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
    return true;
  }
}