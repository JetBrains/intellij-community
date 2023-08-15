// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
abstract class InstallAndEnableTask extends Task.Modal {
  private final boolean allowInstallingPlugins;
  protected final Set<PluginDownloader> myPlugins = new HashSet<>();
  protected final @NotNull Set<PluginId> myPluginIds;
  protected @Nullable List<PluginNode> myCustomPlugins;

  private final boolean myShowDialog;
  private final boolean mySelectAllInDialog;
  protected final @Nullable ModalityState myModalityState;
  protected final @NotNull Runnable myOnSuccess;

  InstallAndEnableTask(@Nullable Project project,
                       @NotNull Set<PluginId> pluginIds,
                       boolean allowInstallingPlugins,
                       boolean showDialog,
                       boolean selectAllInDialog,
                       @Nullable ModalityState modalityState,
                       @NotNull Runnable onSuccess) {
    super(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true);
    this.allowInstallingPlugins = allowInstallingPlugins;
    myPluginIds = pluginIds;
    myShowDialog = showDialog;
    mySelectAllInDialog = selectAllInDialog;
    myModalityState = modalityState;
    myOnSuccess = onSuccess;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      List<PluginNode> marketplacePlugins = MarketplaceRequests.loadLastCompatiblePluginDescriptors(myPluginIds);
      myCustomPlugins = PluginsAdvertiser.loadPluginsFromCustomRepositories(indicator);

      List<IdeaPluginDescriptor> descriptors =
        new ArrayList<>(RepositoryHelper.mergePluginsFromRepositories(marketplacePlugins, myCustomPlugins, true));

      if (myShowDialog) {
        MarketplaceRequests marketplace = MarketplaceRequests.getInstance();
        Set<PluginId> pluginIds = ContainerUtil.map2Set(descriptors, descriptor -> descriptor.getPluginId());
        for (IdeCompatibleUpdate update : MarketplaceRequests.getLastCompatiblePluginUpdate(pluginIds)) {
          int index = ContainerUtil.indexOf(descriptors, d -> d.getPluginId().getIdString().equals(update.getPluginId()));
          if (index != -1) {
            IdeaPluginDescriptor descriptor = descriptors.get(index);
            if (descriptor instanceof PluginNode node) {
              node.setExternalPluginId(update.getExternalPluginId());
              node.setExternalUpdateId(update.getExternalUpdateId());
              node.setDescription(null);

              PluginNode pluginNode = marketplace.loadPluginDetails(node);
              if (pluginNode != null) {
                PluginDetailsPageComponent.loadAllPluginDetails(marketplace, node, pluginNode);
                descriptors.set(index, pluginNode);
              }
            }
          }
        }
      }

      var org = PluginManagerFilters.getInstance();
      for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
        if (!descriptor.isEnabled() && PluginManagerCore.isCompatible(descriptor) &&
            org.allowInstallingPlugin(descriptor) || allowInstallingPlugins) {
          descriptors.add(descriptor);
        }
      }

      for (IdeaPluginDescriptor descriptor : descriptors) {
        if (myPluginIds.contains(descriptor.getPluginId())) {
          myPlugins.add(PluginDownloader.createDownloader(descriptor));
        }
      }
    }
    catch (Exception e) {
      PluginsAdvertiser.getLog().info(e);
    }
  }

  @Override
  public void onSuccess() {
    if (myCustomPlugins == null) {
      return;
    }

    new PluginsAdvertiserDialog(myProject,
                                myPlugins,
                                myCustomPlugins,
                                mySelectAllInDialog,
                                this::runOnSuccess)
      .doInstallPlugins(myShowDialog, myModalityState);
  }

  protected void runOnSuccess(boolean onSuccess) {
    if (onSuccess) {
      myOnSuccess.run();
    }
  }

  public Set<PluginDownloader> getPlugins() { return myPlugins; }
  public @Nullable List<PluginNode> getCustomPlugins() { return myCustomPlugins; }
}
