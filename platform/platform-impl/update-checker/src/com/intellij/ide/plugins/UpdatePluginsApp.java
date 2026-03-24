// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.InternalPluginResults;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * Works in two stages.
 * On the first run, it collects available updates and writes an update script.
 * The second run needs {@code idea.force.plugin.updates = "true"} system property to apply the updates.
 *
 * @see AppMode#FORCE_PLUGIN_UPDATES
 * @see com.intellij.platform.ide.bootstrap.StartupUtil#runActionScript
 */
final class UpdatePluginsApp implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(UpdatePluginsApp.class);
  private static final String OLD_CONFIG_DIR_PROPERTY = "idea.plugin.migration.config.dir";

  @Override
  public void premain(@NotNull List<String> args) {
    System.setProperty("idea.skip.indices.initialization", "true");

    var oldConfig = System.getProperty(OLD_CONFIG_DIR_PROPERTY);
    if (oldConfig != null) {
      RepositoryHelper.updatePluginHostsFromConfigDir(Path.of(oldConfig), LOG);
    }
  }

  @Override
  public void main(@NotNull List<String> args) {
    if (Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
      logInfo("Plugin updates are applied");
      System.exit(0);
    }

    InternalPluginResults updateCheckResult;
    try {
      updateCheckResult = ApplicationManager.getApplication().executeOnPooledThread(
        () -> UpdateChecker.checkInstalledPluginUpdates()
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      logError("Failed to check plugin updates", e);
      System.exit(1);
      return;
    }
    if (!updateCheckResult.getErrors().isEmpty()) {
      LOG.warn(
        "Errors occurred during the update check: " +
        ContainerUtil.map(updateCheckResult.getErrors().entrySet(), entry -> "host=" + entry.getKey() + ": " + entry.getValue().getMessage())
      );
    }

    var pluginsToUpdate = updateCheckResult.getPluginUpdates().getAllEnabled();
    if (args.size() > 1) {
      var filter = new HashSet<>(args.subList(1, args.size()));
      pluginsToUpdate.removeIf(downloader -> !filter.contains(downloader.getId().getIdString()));
    }
    if (pluginsToUpdate.isEmpty()) {
      logInfo("All plugins are up to date.");
      System.exit(0);
      return;
    }

    hotfixInstallDependency(pluginsToUpdate, "Pythonid", "PythonCore");
    hotfixInstallDependency(pluginsToUpdate, "intellij.jupyter", "com.intellij.notebooks.core");
    hotfixInstallDependency(pluginsToUpdate, "R4Intellij", "com.intellij.notebooks.core");
    hotfixInstallDependency(pluginsToUpdate, "com.intellij.bigdatatools.core", "intellij.bigdatatools.coreUi");
    hotfixInstallDependency(pluginsToUpdate, "com.intellij.bigdatatools.core", "intellij.bigdatatools.awsBase");
    hotfixInstallDependency(pluginsToUpdate, "com.intellij.bigdatatools.rfs", "intellij.bigdatatools.gcloud");
    hotfixInstallDependency(pluginsToUpdate, "com.intellij.bigdatatools.rfs", "intellij.bigdatatools.azure");

    logInfo(
      "** Plugins to update: " +
      ContainerUtil.map(pluginsToUpdate, downloader -> downloader.getPluginName() + " version " + downloader.getPluginVersion())
    );

    boolean installed;
    try {
      @SuppressWarnings("UsagesOfObsoleteApi") var indicator = new EmptyProgressIndicator() {
        @Override
        @SuppressWarnings("UseOfSystemOutOrSystemErr")
        public void setText(String text) {
          System.out.println(text);
        }
      };
      installed = ApplicationManager.getApplication().executeOnPooledThread(
        () -> UpdateInstaller.installPluginUpdates(pluginsToUpdate, indicator)
      ).get();
    }
    catch (InterruptedException | ExecutionException e) {
      logError("Failed to install plugin updates", e);
      System.exit(1);
      return;
    }

    if (installed) {
      logInfo("Plugin updates are prepared to be installed");
      System.exit(0);
    }
    else {
      logInfo("Update failed");
      System.exit(1);
    }
  }

  private static void hotfixInstallDependency(Collection<PluginDownloader> downloaders, String pluginIdStr, String dependencyIdStr) {
    var pluginId = PluginId.getId(pluginIdStr);
    var dependencyId = PluginId.getId(dependencyIdStr);
    if (
      PluginManagerCore.isPluginInstalled(dependencyId) ||
      !ContainerUtil.exists(downloaders, p -> p.getId().equals(pluginId)) ||
      ContainerUtil.exists(downloaders, p -> p.getId().equals(dependencyId))
    ) {
      return;
    }

    logInfo("** Hotfix: " + pluginIdStr + " → " + dependencyIdStr);

    var pluginDownloader = requireNonNull(ContainerUtil.find(downloaders, p -> p.getId().equals(pluginId)));
    if (!ContainerUtil.exists(pluginDownloader.getDescriptor().getDependencies(), d -> d.getPluginId().equals(dependencyId))) {
      logInfo("Plugin " + pluginId + " does not depend on " + dependencyId);
      return;
    }

    PluginNode dependencyNode;
    try {
      dependencyNode = ApplicationManager.getApplication().executeOnPooledThread(
        () -> {
          var uiModel = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdateModel(dependencyId);
          return uiModel != null && uiModel.getDescriptor() instanceof PluginNode pluginNode ? pluginNode : null;
        }
      ).get();
      if (dependencyNode == null) {
        logInfo("Failed to find a suitable " + dependencyId + " plugin");
        return;
      }
    }
    catch (InterruptedException | ExecutionException e) {
      logError("Failed to process " + pluginId + " plugin dependencies", null);
      return;
    }

    try {
      var dependencyDownloader = PluginDownloader.createDownloader(dependencyNode);
      logInfo("Added a required dependency for " + pluginId + " plugin for installation: " + dependencyId);
      downloaders.add(dependencyDownloader);
    }
    catch (IOException e) {
      logError("Failed to create a plugin downloader", e);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logInfo(String message) {
    System.out.println(message);
    LOG.info(message);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logError(String message, @Nullable Throwable t) {
    System.err.println("error: " + message);
    LOG.error(message, t);
  }
}
