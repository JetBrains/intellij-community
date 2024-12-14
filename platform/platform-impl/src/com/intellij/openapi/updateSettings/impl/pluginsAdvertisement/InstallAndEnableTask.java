// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagementPolicy;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.Set;

@ApiStatus.Internal
public final class InstallAndEnableTask extends Task.Modal {
  private final Set<PluginId> myPluginIds;
  private final boolean myShowDialog;
  private final boolean mySelectAllInDialog;
  private final @Nullable ModalityState myModalityState;
  private final Runnable myOnSuccess;

  private final Set<PluginDownloader> myPlugins = new HashSet<>();

  InstallAndEnableTask(
    @Nullable Project project,
    @NotNull Set<PluginId> pluginIds,
    boolean showDialog,
    boolean selectAllInDialog,
    @Nullable ModalityState modalityState,
    @NotNull Runnable onSuccess
  ) {
    super(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true);
    myPluginIds = pluginIds;
    myShowDialog = showDialog;
    mySelectAllInDialog = selectAllInDialog;
    myModalityState = modalityState;
    myOnSuccess = onSuccess;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      var descriptors = new ArrayList<IdeaPluginDescriptor>(MarketplaceRequests.loadLastCompatiblePluginDescriptors(myPluginIds));

      if (myShowDialog) {
        var marketplace = MarketplaceRequests.getInstance();
        var pluginIds = ContainerUtil.map2Set(descriptors, descriptor -> descriptor.getPluginId());
        for (var update : MarketplaceRequests.getLastCompatiblePluginUpdate(pluginIds)) {
          var index = ContainerUtil.indexOf(descriptors, d -> d.getPluginId().getIdString().equals(update.getPluginId()));
          if (index != -1) {
            var descriptor = descriptors.get(index);
            if (descriptor instanceof PluginNode node) {
              node.setExternalPluginId(update.getExternalPluginId());
              node.setExternalUpdateId(update.getExternalUpdateId());
              node.setDescription(null);

              var pluginNode = marketplace.loadPluginDetails(node);
              if (pluginNode != null) {
                PluginDetailsPageComponent.loadAllPluginDetails(marketplace, node, pluginNode);
                descriptors.set(index, pluginNode);
              }
            }
          }
        }
      }

      for (var descriptor : PluginManagerCore.getPlugins()) {
        if (!descriptor.isEnabled() &&
            PluginManagerCore.isCompatible(descriptor) &&
            PluginManagementPolicy.getInstance().canInstallPlugin(descriptor)) {
          descriptors.add(descriptor);
        }
      }

      for (var descriptor : descriptors) {
        if (myPluginIds.contains(descriptor.getPluginId())) {
          myPlugins.add(PluginDownloader.createDownloader(descriptor));
        }
      }
    }
    catch (Exception e) {
      Logger.getInstance(InstallAndEnableTask.class).info(e);
    }
  }

  @Override
  public void onSuccess() {
    new PluginsAdvertiserDialog(myProject, myPlugins, mySelectAllInDialog, this::runOnSuccess)
      .doInstallPlugins(myShowDialog, myModalityState);
  }

  private void runOnSuccess(boolean onSuccess) {
    if (onSuccess) {
      myOnSuccess.run();
    }
  }

  public @NotNull Set<PluginDownloader> getPlugins() { return myPlugins; }
}
