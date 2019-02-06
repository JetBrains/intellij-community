// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PluginAdvertiserEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.type.associations.detected");
  private final Set<String> myEnabledExtensions = new HashSet<>();

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (file.getFileType() != PlainTextFileType.INSTANCE && !(file.getFileType() instanceof AbstractFileType)) return null;

    final String extension = file.getExtension();
    final String fileName = file.getName();
    if (extension != null && isIgnored("*." + extension, project) || isIgnored(fileName, project)) return null;

    final PluginsAdvertiser.KnownExtensions knownExtensions = PluginsAdvertiser.loadExtensions();
    if (knownExtensions != null) {
      final EditorNotificationPanel panel = extension != null ? createPanel("*." + extension, knownExtensions, project) : null;
      if (panel != null) {
        return panel;
      }
      return createPanel(fileName, knownExtensions, project);
    }
    return null;
  }

  private boolean isIgnored(String extension, @NotNull Project project) {
    return myEnabledExtensions.contains(extension) ||
           UnknownFeaturesCollector.getInstance(project).isIgnored(createExtensionFeature(extension));
  }

  private EditorNotificationPanel createPanel(String extension, PluginsAdvertiser.KnownExtensions knownExtensions, @NotNull Project project) {
    final Set<PluginsAdvertiser.Plugin> plugins = knownExtensions.find(extension);
    if (plugins != null && !plugins.isEmpty()) {
      return createPanel(extension, plugins, project);
    }
    return null;
  }

  @Nullable
  private EditorNotificationPanel createPanel(final String extension, final Set<PluginsAdvertiser.Plugin> plugins, @NotNull Project project) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();

    panel.setText("Plugins supporting " + extension + " files found.");
    final IdeaPluginDescriptor disabledPlugin = PluginsAdvertiser.getDisabledPlugin(plugins);
    if (disabledPlugin != null) {
      panel.createActionLabel("Enable " + disabledPlugin.getName() + " plugin", () -> {
        myEnabledExtensions.add(extension);
        EditorNotifications.getInstance(project).updateAllNotifications();
        PluginsAdvertiser.enablePlugins(project, Collections.singletonList(disabledPlugin));
      });
    } else if (hasNonBundledPlugin(plugins)) {
      panel.createActionLabel("Install plugins", () -> {
        Set<String> pluginIds = new HashSet<>();
        for (PluginsAdvertiser.Plugin plugin : plugins) {
          pluginIds.add(plugin.myPluginId);
        }
        PluginsAdvertiser.installAndEnablePlugins(pluginIds, () -> {
          myEnabledExtensions.add(extension);
          EditorNotifications.getInstance(project).updateAllNotifications();
        });
      });
    } else if (PluginsAdvertiser.hasBundledPluginToInstall(plugins) != null){
      if (PropertiesComponent.getInstance().isTrueValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
        return null;
      }
      panel.setText(extension + " files are supported by " + PluginsAdvertiser.IDEA_ULTIMATE_EDITION);

      panel.createActionLabel(PluginsAdvertiser.CHECK_ULTIMATE_EDITION_TITLE, () -> {
        myEnabledExtensions.add(extension);
        PluginsAdvertiser.openDownloadPage();
      });

      panel.createActionLabel(PluginsAdvertiser.ULTIMATE_EDITION_SUGGESTION, () -> {
        PropertiesComponent.getInstance().setValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION, "true");
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
    } else {
      return null;
    }
    panel.createActionLabel("Ignore extension", () -> {
      UnknownFeaturesCollector.getInstance(project).ignoreFeature(createExtensionFeature(extension));
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }

  private static boolean hasNonBundledPlugin(Set<PluginsAdvertiser.Plugin> plugins) {
    for (PluginsAdvertiser.Plugin plugin : plugins) {
      if (!plugin.myBundled) return true;
    }
    return false;
  }

  private static UnknownFeature createExtensionFeature(String extension) {
    return new UnknownFeature(FileTypeFactory.FILE_TYPE_FACTORY_EP.getName(), "File Type", extension, extension);
  }
}
