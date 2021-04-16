// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.function.Consumer;

/**
 * @author anna
 */
public final class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialog.class);

  private final SortedSet<PluginDownloader> myPluginToInstall = new TreeSet<>(Comparator.comparing(PluginDownloader::getPluginName, String::compareToIgnoreCase));
  private final Set<PluginId> mySkippedPlugins = new HashSet<>();
  private final @Nullable Project myProject;
  private final @NotNull List<PluginNode> myCustomPlugins;
  private final @Nullable Consumer<? super Boolean> myFinishFunction;

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Set<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins,
                          @Nullable Consumer<? super Boolean> finishFunction) {
    super(project);
    myProject = project;
    myPluginToInstall.addAll(pluginsToInstall);
    myCustomPlugins = customPlugins;
    myFinishFunction = finishFunction;
    setTitle(IdeBundle.message("dialog.title.choose.plugins.to.install.or.enable"));
    init();
  }

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Set<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins) {
    this(project, pluginsToInstall, customPlugins, null);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return new DetectedPluginsPanel() {

      {
        addAll(myPluginToInstall);
        TableUtil.ensureSelectionExists(getEntryTable());
      }

      @Override
      protected @NotNull Set<PluginId> getSkippedPlugins() {
        return mySkippedPlugins;
      }
    };
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
    Set<IdeaPluginDescriptor> pluginsToEnable = new HashSet<>();
    List<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myPluginToInstall) {
      IdeaPluginDescriptor plugin = downloader.getDescriptor();
      if (!mySkippedPlugins.contains(plugin.getPluginId())) {
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(downloader.toPluginNode());
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return false;
    }

    PluginManagerMain.PluginEnabler pluginHelper = new PluginManagerMain.PluginEnabler.HEADLESS();
    PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginHelper, nodes);

    Runnable notifyRunnable = () -> {
      boolean notInstalled = nodes
        .stream()
        .map(PluginNode::getPluginId)
        .anyMatch(pluginId -> PluginManagerCore.getPlugin(pluginId) == null);
      if (notInstalled) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };
    DisabledPluginsState.enablePlugins(pluginsToEnable, true);
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes,
                                          myCustomPlugins,
                                          true,
                                          notifyRunnable,
                                          pluginHelper,
                                          myFinishFunction);
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