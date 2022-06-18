// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class InstallAndEnableTask extends Task.Modal {

  private final Set<PluginDownloader> myPlugins = new HashSet<>();
  private final @NotNull Set<PluginId> myPluginIds;
  private final boolean myShowDialog;
  private final boolean mySelectAllInDialog;
  private final @Nullable ModalityState myModalityState;
  private @NotNull final Runnable myOnSuccess;
  private @Nullable List<PluginNode> myCustomPlugins;

  InstallAndEnableTask(@Nullable Project project,
                       @NotNull Set<PluginId> pluginIds,
                       boolean showDialog,
                       boolean selectAllInDialog,
                       @Nullable ModalityState modalityState,
                       @NotNull Runnable onSuccess) {
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
      List<PluginNode> marketplacePlugins = MarketplaceRequests.loadLastCompatiblePluginDescriptors(myPluginIds);
      myCustomPlugins = PluginsAdvertiser.loadPluginsFromCustomRepositories(indicator);

      List<IdeaPluginDescriptor> descriptors =
        new ArrayList<>(RepositoryHelper.mergePluginsFromRepositories(marketplacePlugins, myCustomPlugins, true));

      var org = PluginManagerFilters.getInstance();
      for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
        if (!descriptor.isEnabled() && PluginManagerCore.isCompatible(descriptor) && org.allowInstallingPlugin(descriptor)) {
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

  private void runOnSuccess(boolean onSuccess) {
    if (onSuccess) {
      myOnSuccess.run();
    }
  }
}
