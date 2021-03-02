// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.advertiser.PluginData;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PluginAdvertiserEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.type.associations.detected");
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    PluginAdvertiserExtensionsStateService extensionsStateService = PluginAdvertiserExtensionsStateService.getInstance();
    PluginAdvertiserExtensionsStateService.ExtensionDataProvider pluginAdvertiserExtensionsState =
      extensionsStateService.createExtensionDataProvider(project);
    PluginAdvertiserExtensionsData extensionsData = pluginAdvertiserExtensionsState.requestExtensionData(file);

    Set<String> jbPluginsIds = MarketplaceRequests.getInstance().getJetBrainsPluginsIds();
    if (extensionsData == null || jbPluginsIds == null) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        MarketplaceRequests.getInstance().loadJetBrainsPluginsIds();
        boolean shouldUpdateNotifications = extensionsStateService.updateCache(file.getName());
        String fullExtension = PluginAdvertiserExtensionsStateService.getFullExtension(file);
        if (fullExtension != null) {
          shouldUpdateNotifications = extensionsStateService.updateCache(fullExtension) || shouldUpdateNotifications;
        }
        if (shouldUpdateNotifications) {
          EditorNotifications.getInstance(project).updateNotifications(file);
        }
        LOG.debug(String.format("Tried to update extensions cache for file '%s'. shouldUpdateNotifications=%s", file.getName(),
                                shouldUpdateNotifications));
      });
      return null;
    }

    String extensionOrFileName = extensionsData.getExtensionOrFileName();
    Set<PluginData> dataSet = extensionsData.getPlugins();

    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName));

    Runnable onPluginsInstalled = () -> {
      pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
      updateAllNotifications(project);
    };

    PluginsToInstall pluginsToInstall = new PluginsToInstall(dataSet, jbPluginsIds);
    IdeaPluginDescriptor disabledPlugin = pluginsToInstall.myDisabledPlugin;

    if (disabledPlugin != null) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin.getName()), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
        updateAllNotifications(project);

        FUSEventSource.EDITOR.logEnablePlugins(List.of(disabledPlugin.getPluginId().getIdString()),
                                               project);
        PluginManagerConfigurable.showPluginConfigurableAndEnable(project, Set.of(disabledPlugin));
      });
    }
    else if (!pluginsToInstall.myJbProduced.isEmpty()) {
      createInstallActionLabel(panel, pluginsToInstall.myJbProduced, onPluginsInstalled);
    }
    else if (!PluginsAdvertiser.getBundledPluginToInstall(dataSet).isEmpty()) {
      if (PluginsAdvertiser.isIgnoreUltimate()) {
        return null;
      }
      panel.setText(IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName));

      //noinspection DialogTitleCapitalization
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate"), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
        FUSEventSource.EDITOR.openDownloadPageAndLog(project);
      });

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate"), () -> {
        FUSEventSource.EDITOR.doIgnoreUltimateAndLog(project);
        updateAllNotifications(project);
      });
    }
    else if (!pluginsToInstall.myThirdParty.isEmpty()) {
      createInstallActionLabel(panel, pluginsToInstall.myThirdParty, onPluginsInstalled);
    }
    else {
      return null;
    }
    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.extension"), () -> {
      FUSEventSource.EDITOR.logIgnoreExtension(project);
      pluginAdvertiserExtensionsState.ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
      updateAllNotifications(project);
    });
    return panel;
  }

  private static class PluginsToInstall {
    private IdeaPluginDescriptor myDisabledPlugin = null;
    private final Set<PluginData> myJbProduced = new HashSet<>();
    private final Set<PluginData> myThirdParty = new HashSet<>();

    private PluginsToInstall(@NotNull Set<PluginData> dataSet,
                             @NotNull Set<String> jbPluginsIds) {
      Map<PluginId, IdeaPluginDescriptorImpl> descriptorsById = PluginManagerCore.buildPluginIdMap();
      for (PluginData data : dataSet) {
        IdeaPluginDescriptor installedPlugin = descriptorsById.get(data.getPluginId());

        if (installedPlugin != null) {
          if (!installedPlugin.isEnabled() && myDisabledPlugin == null) myDisabledPlugin = installedPlugin;
        }
        else if (!data.isBundled()) {
          myThirdParty.add(data);
          if (jbPluginsIds.contains(data.getPluginIdString())) {
            myJbProduced.add(data);
          }
        }
      }
    }
  }

  private static void createInstallActionLabel(@NotNull EditorNotificationPanel panel,
                                               @NotNull Set<PluginData> dataSet,
                                               @NotNull Runnable onSuccess) {
    Set<PluginId> pluginIds = ContainerUtil.map2Set(dataSet, PluginData::getPluginId);
    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins"),
                            () -> {
                              FUSEventSource.EDITOR.logInstallPlugins(ContainerUtil.map(pluginIds, PluginId::getIdString));
                              PluginsAdvertiser.installAndEnable(pluginIds, onSuccess);
                            });
  }

  private static void updateAllNotifications(@NotNull Project project) {
    EditorNotifications.getInstance(project).updateAllNotifications();
  }
}
