// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

final class PluginsAdvertiserStartupActivity implements StartupActivity.Background {
  private final Object myListRefreshLock = new Object();
  private boolean myListRefreshed = false;

  @Override
  public void runActivity(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || !UpdateSettings.getInstance().isCheckNeeded()) {
      return;
    }

    synchronized (myListRefreshLock) {
      if (!myListRefreshed) {
        myListRefreshed = true;
        PluginsAdvertiser.ensureDeleted();
      }
    }

    try {
      run(project);
    }
    catch (UnknownHostException e) {
      PluginsAdvertiser.LOG.warn("Host name could not be resolved: " + e.getMessage());
    }
    catch (Exception e) {
      PluginsAdvertiser.LOG.info(e);
    }
  }

  private void run(@NotNull Project project) throws IOException {
    Set<UnknownFeature> unknownFeatures = UnknownFeaturesCollector.getInstance(project).getUnknownFeatures();
    final PluginsAdvertiser.KnownExtensions extensions = PluginsAdvertiser.loadExtensions();
    if (extensions != null && unknownFeatures.isEmpty()) {
      return;
    }
    MultiMap<PluginId, UnknownFeature> features = new MultiMap<>();
    Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor> disabledPlugins = new THashMap<>();
    List<IdeaPluginDescriptor> customPlugins = RepositoryHelper.loadPluginsFromCustomRepositories(null);
    if (project.isDisposed()) {
      return;
    }
    if (extensions == null) {
      PluginsAdvertiser.loadAllExtensions(ContainerUtil.map2Set(customPlugins, it -> it.getPluginId().getIdString()));
      if (project.isDisposed()) return;
      EditorNotifications.getInstance(project).updateAllNotifications();
    }
    final Map<PluginId, PluginsAdvertiser.Plugin> ids = new HashMap<>();
    for (UnknownFeature feature : unknownFeatures) {
      ProgressManager.checkCanceled();
      final List<PluginsAdvertiser.Plugin> pluginId = PluginsAdvertiser.retrieve(feature);
      if (!pluginId.isEmpty()) {
        for (PluginsAdvertiser.Plugin plugin : pluginId) {
          PluginId id = PluginId.getId(plugin.myPluginId);
          ids.put(id, plugin);
          features.putValue(id, feature);
        }
      }
    }

    //include disabled plugins
    for (PluginId id : ids.keySet()) {
      PluginsAdvertiser.Plugin plugin = ids.get(id);
      if (PluginManagerCore.isDisabled(id)) {
        final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(id);
        if (pluginDescriptor != null) {
          disabledPlugins.put(plugin, pluginDescriptor);
        }
      }
    }

    List<String> bundledPlugin = PluginsAdvertiser.hasBundledPluginToInstall(ids.values());
    Set<PluginDownloader> plugins = new THashSet<>();

    if (!ids.isEmpty()) {
      List<PluginNode> marketplacePlugins = MarketplaceRequests.getInstance()
        .loadLastCompatiblePluginDescriptors(ContainerUtil.map(ids.keySet(), it -> it.getIdString()));

      List<IdeaPluginDescriptor> compatibleUpdates = UpdateChecker.mergePluginsFromRepositories(marketplacePlugins, customPlugins);

      for (IdeaPluginDescriptor loadedPlugin : compatibleUpdates) {
        IdeaPluginDescriptor existingPlugin = PluginManagerCore.getPlugin(loadedPlugin.getPluginId());
        if (existingPlugin != null &&
            PluginDownloader.compareVersionsSkipBrokenAndIncompatible(loadedPlugin.getVersion(), existingPlugin) <= 0) {
          continue;
        }
        PluginId pluginId = loadedPlugin.getPluginId();
        if (ids.containsKey(pluginId) &&
            !PluginManagerCore.isDisabled(pluginId) &&
            !PluginManagerCore.isBrokenPlugin(loadedPlugin)) {
          plugins.add(PluginDownloader.createDownloader(loadedPlugin));
        }
      }
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) {
        return;
      }

      List<NotificationAction> notificationActions = new ArrayList<>();

      String message = null;
      if (!plugins.isEmpty() || !disabledPlugins.isEmpty()) {
        message = getAddressedMessagePresentation(plugins, disabledPlugins, features);
        if (!disabledPlugins.isEmpty()) {
          notificationActions.add(NotificationAction.createSimpleExpiring(
            IdeBundle.message("plugins.advertiser.action.enable.plugins"), () -> {
              FeatureUsageData data = new FeatureUsageData()
                .addData("source", "notification")
                .addData("plugins", ContainerUtil.map(disabledPlugins.values(), (plugin) -> plugin.getPluginId().getIdString()));
              FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "enable.plugins", data);
              PluginsAdvertiser.enablePlugins(project, disabledPlugins.values());
            }));
        }
        else {
          notificationActions.add(NotificationAction.createSimpleExpiring(
            IdeBundle.message("plugins.advertiser.action.configure.plugins"), () -> {
              FeatureUsageData data = new FeatureUsageData().addData("source", "notification");
              FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "configure.plugins", data);
              new PluginsAdvertiserDialog(project, plugins.toArray(new PluginDownloader[0]), customPlugins).show();
            }));
        }
        notificationActions.add(NotificationAction.createSimpleExpiring(
          IdeBundle.message("plugins.advertiser.action.ignore.unknown.features"),
          () -> {
            FeatureUsageData data = new FeatureUsageData().addData("source", "notification");
            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.unknown.features", data);
            UnknownFeaturesCollector featuresCollector = UnknownFeaturesCollector.getInstance(project);
            for (UnknownFeature feature : unknownFeatures) {
              featuresCollector.ignoreFeature(feature);
            }
          }));
      }
      else if (bundledPlugin != null && !PropertiesComponent.getInstance().isTrueValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
        message = IdeBundle.message("plugins.advertiser.ultimate.features.detected", StringUtil.join(bundledPlugin, ", "));
        notificationActions.add(NotificationAction.createSimpleExpiring(
          IdeBundle.message("plugins.advertiser.action.try.ultimate"), () -> {
            FeatureUsageData data = new FeatureUsageData().addData("source", "notification");
            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "open.download.page", data);
            PluginsAdvertiser.openDownloadPage();
          }));
        notificationActions.add(NotificationAction.createSimpleExpiring(
          IdeBundle.message("plugins.advertiser.action.ignore.ultimate"), () -> {
            FeatureUsageData data = new FeatureUsageData().addData("source", "notification");
            FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.ultimate", data);
            PropertiesComponent.getInstance().setValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION, "true");
          }));
      }

      if (message != null) {
        Notification notification = PluginsAdvertiser.NOTIFICATION_GROUP
          .createNotification("", message, NotificationType.INFORMATION, null);
        for (NotificationAction action : notificationActions) {
          notification.addAction(action);
        }
        notification.notify(project);
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  private static String getAddressedMessagePresentation(@NotNull Set<PluginDownloader> plugins,
                                                        @NotNull Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor> disabledPlugins,
                                                        @NotNull MultiMap<PluginId, UnknownFeature> features) {
    final MultiMap<String, String> addressedFeatures = MultiMap.createSet();
    final Set<PluginId> ids = new LinkedHashSet<>();
    for (PluginDownloader plugin : plugins) {
      ids.add(plugin.getId());
    }
    for (PluginsAdvertiser.Plugin plugin : disabledPlugins.keySet()) {
      ids.add(PluginId.getId(plugin.myPluginId));
    }
    for (PluginId id : ids) {
      for (UnknownFeature feature : features.get(id)) {
        addressedFeatures.putValue(feature.getFeatureDisplayName(), feature.getImplementationDisplayName());
      }
    }
    final String addressedFeaturesPresentation = StringUtil.join(addressedFeatures.entrySet(),
            entry -> entry.getKey() + ": " + StringUtil.join(entry.getValue(), ", "), "; ");
    final int addressedFeaturesNumber = addressedFeatures.keySet().size();
    final int pluginsNumber = ids.size();
    return StringUtil.pluralize("Plugin", pluginsNumber) + " supporting " + StringUtil.pluralize("feature", addressedFeaturesNumber) +
           " (" + addressedFeaturesPresentation + ") " + (pluginsNumber == 1 ? "is" : "are") + " currently " + (plugins.isEmpty() ? "disabled" : "not installed") + ".<br>";
  }
}