// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

final class PluginsAdvertiserStartupActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      return;
    }

    if (!UpdateSettings.getInstance().isCheckNeeded()) {
      return;
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

  private static void run(@NotNull Project project) throws IOException {
    Set<UnknownFeature> unknownFeatures = UnknownFeaturesCollector.getInstance(project).getUnknownFeatures();
    final PluginsAdvertiser.KnownExtensions extensions = PluginsAdvertiser.loadExtensions();
    if (extensions != null && unknownFeatures.isEmpty()) {
      return;
    }

    MultiMap<String, UnknownFeature> features = new MultiMap<>();
    Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor> disabledPlugins = new THashMap<>();
    List<IdeaPluginDescriptor> allPlugins = RepositoryHelper.loadPluginsFromAllRepositories(null);
    if (project.isDisposed()) {
      return;
    }
    if (extensions == null) {
      PluginsAdvertiser.loadSupportedExtensions(allPlugins);
      if (project.isDisposed()) return;
      EditorNotifications.getInstance(project).updateAllNotifications();
    }
    final Map<String, PluginsAdvertiser.Plugin> ids = new HashMap<>();
    for (UnknownFeature feature : unknownFeatures) {
      ProgressManager.checkCanceled();
      final List<PluginsAdvertiser.Plugin> pluginId = PluginsAdvertiser.retrieve(feature);
      if (pluginId != null) {
        for (PluginsAdvertiser.Plugin plugin : pluginId) {
          ids.put(plugin.myPluginId, plugin);
          features.putValue(plugin.myPluginId, feature);
        }
      }
    }

    //include disabled plugins
    for (String id : ids.keySet()) {
      PluginsAdvertiser.Plugin plugin = ids.get(id);
      if (PluginManagerCore.isDisabled(id)) {
        final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId(id));
        if (pluginDescriptor != null) {
          disabledPlugins.put(plugin, pluginDescriptor);
        }
      }
    }

    List<String> bundledPlugin = PluginsAdvertiser.hasBundledPluginToInstall(ids.values());
    Set<PluginDownloader> plugins = new THashSet<>();
    for (IdeaPluginDescriptor loadedPlugin : allPlugins) {
      PluginId pluginId = loadedPlugin.getPluginId();
      if (ids.containsKey(pluginId.getIdString()) &&
          !PluginManagerCore.isDisabled(pluginId.getIdString()) &&
          !PluginManagerCore.isBrokenPlugin(loadedPlugin)) {
        plugins.add(PluginDownloader.createDownloader(loadedPlugin));
      }
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) {
        return;
      }

      String message = null;
      if (!plugins.isEmpty() || !disabledPlugins.isEmpty()) {
        message = getAddressedMessagePresentation(plugins, disabledPlugins, features);
        if (!disabledPlugins.isEmpty()) {
          message += "<a href=\"enable\">Enable plugins...</a><br>";
        }
        else {
          message += "<a href=\"configure\">Configure plugins...</a><br>";
        }

        message += "<a href=\"ignore\">Ignore Unknown Features</a>";
      }
      else if (bundledPlugin != null && !PropertiesComponent.getInstance().isTrueValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
        message = "Features covered by " + PluginsAdvertiser.IDEA_ULTIMATE_EDITION +
                  " (" + StringUtil.join(bundledPlugin, ", ") + ") are detected.<br>" +
                  "<a href=\"open\">" + PluginsAdvertiser.CHECK_ULTIMATE_EDITION_TITLE + "</a><br>" +
                  "<a href=\"ignoreUltimate\">" + PluginsAdvertiser.ULTIMATE_EDITION_SUGGESTION + "</a>";
      }

      if (message != null) {
        PluginsAdvertiser.ConfigurePluginsListener
          notificationListener = new PluginsAdvertiser.ConfigurePluginsListener(unknownFeatures, project, allPlugins, plugins, disabledPlugins);
        PluginsAdvertiser.NOTIFICATION_GROUP
          .createNotification(PluginsAdvertiser.DISPLAY_ID, message, NotificationType.INFORMATION, notificationListener).notify(project);
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  private static String getAddressedMessagePresentation(@NotNull Set<PluginDownloader> plugins,
                                                        @NotNull Map<PluginsAdvertiser.Plugin, IdeaPluginDescriptor> disabledPlugins,
                                                        @NotNull MultiMap<String, UnknownFeature> features) {
    final MultiMap<String, String> addressedFeatures = MultiMap.createSet();
    final Set<String> ids = new LinkedHashSet<>();
    for (PluginDownloader plugin : plugins) {
      ids.add(plugin.getPluginId());
    }
    for (PluginsAdvertiser.Plugin plugin : disabledPlugins.keySet()) {
      ids.add(plugin.myPluginId);
    }
    for (String id : ids) {
      for (UnknownFeature feature : features.get(id)) {
        addressedFeatures.putValue(feature.getFeatureDisplayName(), feature.getImplementationDisplayName());
      }
    }
    final String addressedFeaturesPresentation = StringUtil.join(addressedFeatures.entrySet(),
                                                                           entry -> entry.getKey() + "[" + StringUtil.join(entry.getValue(), ", ") + "]", ", ");
    final int addressedFeaturesNumber = addressedFeatures.keySet().size();
    final int pluginsNumber = ids.size();
    return StringUtil.pluralize("Plugin", pluginsNumber) + " supporting " + StringUtil.pluralize("feature", addressedFeaturesNumber) +
           " (" + addressedFeaturesPresentation + ") " + (pluginsNumber == 1 ? "is" : "are") + " currently " + (plugins.isEmpty() ? "disabled" : "not installed") + ".<br>";
  }
}
