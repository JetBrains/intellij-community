// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PluginAdvertiserEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.type.associations.detected");

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
    final EditorNotificationPanel panel = new EditorNotificationPanel();

    PluginAdvertiserExtensionsState pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsState.getInstance(project);
    PluginAdvertiserExtensionsKey extensionsKey = new PluginAdvertiserExtensionsKey(file.getName(), file.getFileType().getName(), file.getExtension());
    PluginAdvertiserExtensionsData cachedData = pluginAdvertiserExtensionsState.getCachedData(extensionsKey);
    if (cachedData == null) {
      return null;
    }
    String extensionOrFileName = cachedData.getExtensionOrFileName();
    Set<PluginsAdvertiser.Plugin> plugins = cachedData.getPlugins();

    panel.setText(IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName));
    final IdeaPluginDescriptor disabledPlugin = PluginsAdvertiser.getDisabledPlugin(plugins);
    if (disabledPlugin != null) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin.getName()), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileName(extensionOrFileName);
        pluginAdvertiserExtensionsState.invalidateCacheForKey(extensionsKey);
        EditorNotifications.getInstance(project).updateAllNotifications();
        FeatureUsageData data = new FeatureUsageData()
          .addData("source", "editor")
          .addData("plugins", Collections.singletonList(disabledPlugin.getPluginId().getIdString()));
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "enable.plugins", data);
        PluginsAdvertiser.enablePlugins(project, Collections.singletonList(disabledPlugin));
      });
    }
    else if (hasNonBundledPlugin(plugins)) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins"), () -> {
        Set<PluginId> pluginIds = new HashSet<>();
        for (PluginsAdvertiser.Plugin plugin : plugins) {
          pluginIds.add(PluginId.getId(plugin.myPluginId));
        }
        FeatureUsageData data = new FeatureUsageData()
          .addData("source", "editor")
          .addData("plugins", ContainerUtil.map(pluginIds, (pluginId) -> pluginId.getIdString()));
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "install.plugins", data);
        PluginsAdvertiser.installAndEnable(pluginIds, () -> {
          pluginAdvertiserExtensionsState.addEnabledExtensionOrFileName(extensionOrFileName);
          pluginAdvertiserExtensionsState.invalidateCacheForKey(extensionsKey);
          EditorNotifications.getInstance(project).updateAllNotifications();
        });
      });
    }
    else if (PluginsAdvertiser.hasBundledPluginToInstall(plugins) != null) {
      if (PropertiesComponent.getInstance().isTrueValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
        return null;
      }
      panel.setText(IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName));

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate"), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileName(extensionOrFileName);
        pluginAdvertiserExtensionsState.invalidateCacheForKey(extensionsKey);
        FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "open.download.page", data);
        PluginsAdvertiser.openDownloadPage();
      });

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate"), () -> {
        FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.ultimate", data);
        PropertiesComponent.getInstance().setValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION, "true");
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
    }
    else {
      return null;
    }
    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.extension"), () -> {
      FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
      FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.extensions", data);
      UnknownFeaturesCollector.getInstance(project).ignoreFeature(createUnknownExtensionFeature(extensionOrFileName));
      pluginAdvertiserExtensionsState.invalidateCacheForKey(extensionsKey);
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }

  private static boolean hasNonBundledPlugin(@NotNull Set<? extends PluginsAdvertiser.Plugin> plugins) {
    for (PluginsAdvertiser.Plugin plugin : plugins) {
      if (!plugin.myBundled) return true;
    }
    return false;
  }

  @NotNull
  private static UnknownFeature createUnknownExtensionFeature(@NotNull String extension) {
    //noinspection deprecation
    return new UnknownFeature(FileTypeFactory.FILE_TYPE_FACTORY_EP.getName(), "File Type", extension, extension);
  }
}
