// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PluginsAdvertiserDialogPluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialogPluginInstaller.class);

  private final Collection<PluginDownloader> myPluginToInstall;
  private final @Nullable Project myProject;
  private final @NotNull List<PluginNode> myCustomPlugins;
  private final @Nullable Consumer<? super Boolean> myFinishFunction;

  public PluginsAdvertiserDialogPluginInstaller(@Nullable Project project,
                          @NotNull Collection<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins,
                          @Nullable Consumer<? super Boolean> finishFunction) {
    myProject = project;
    myPluginToInstall = pluginsToInstall;
    myCustomPlugins = customPlugins;
    myFinishFunction = finishFunction;
  }

  public boolean doInstallPlugins(@NotNull Predicate<? super PluginDownloader> predicate, @NotNull ModalityState modalityState) {
    ArrayList<IdeaPluginDescriptor> pluginsToEnable = new ArrayList<>();
    ArrayList<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myPluginToInstall) {
      if (predicate.test(downloader)) {
        IdeaPluginDescriptor plugin = downloader.getDescriptor();
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(downloader.toPluginNode());
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return false;
    }

    var org = PluginManagerFilters.getInstance();
    var notAllowedToInstallPlugins = nodes
      .stream()
      .filter(descriptor -> !org.allowInstallingPlugin(descriptor))
      .map(e -> e.getPluginId().getIdString())
      .collect(Collectors.toCollection(TreeSet<String>::new));

    if (!notAllowedToInstallPlugins.isEmpty()) {
      LOG.warn("Some advertised plugins were not allowed to install for " +
               "the organization: " + StringUtil.join(notAllowedToInstallPlugins, ", "));
      return false;
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(PluginEnabler.HEADLESS, nodes);

    Runnable notifyRunnable = () -> {
      boolean notInstalled = nodes.stream()
        .map(PluginNode::getPluginId)
        .map(PluginManagerCore::getPlugin)
        .anyMatch(Objects::isNull);
      if (notInstalled) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };

    PluginEnabler.HEADLESS.enable(pluginsToEnable);
    if (!nodes.isEmpty()) {
      try {
        downloadPlugins(nodes, myCustomPlugins, notifyRunnable, modalityState, myFinishFunction);
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

  public boolean downloadPlugins(
    @NotNull List<PluginNode> plugins,
                                 @NotNull Collection<PluginNode> customPlugins,
                                 @Nullable Runnable onSuccess,
                                 @NotNull final ModalityState modalityState,
                                 @Nullable Consumer<? super Boolean> function) throws IOException {
    return PluginManagerMain.downloadPlugins(plugins,
                                             customPlugins,
                                             true,
                                             onSuccess,
                                             PluginEnabler.HEADLESS,
                                             modalityState,
                                             function);
  }
}
