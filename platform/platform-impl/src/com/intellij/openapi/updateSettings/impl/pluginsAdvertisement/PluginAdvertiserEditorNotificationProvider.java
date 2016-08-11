/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
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

/**
 * User: anna
 * Date: 10/11/13
 */
public class PluginAdvertiserEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.type.associations.detected");
  private final Project myProject;
  private final EditorNotifications myNotifications;
  private final Set<String> myEnabledExtensions = new HashSet<>();

  public PluginAdvertiserEditorNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    myNotifications = notifications;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() != PlainTextFileType.INSTANCE) return null;

    final String extension = file.getExtension();
    final String fileName = file.getName();
    if (extension != null && isIgnored("*." + extension) || isIgnored(fileName)) return null;

    final PluginsAdvertiser.KnownExtensions knownExtensions = PluginsAdvertiser.loadExtensions();
    if (knownExtensions != null) {
      final EditorNotificationPanel panel = extension != null ? createPanel("*." + extension, knownExtensions) : null;
      if (panel != null) {
        return panel;
      }
      return createPanel(fileName, knownExtensions);
    }
    return null;
  }

  private boolean isIgnored(String extension) {
    return myEnabledExtensions.contains(extension) ||
           UnknownFeaturesCollector.getInstance(myProject).isIgnored(createExtensionFeature(extension));
  }

  private EditorNotificationPanel createPanel(String extension, PluginsAdvertiser.KnownExtensions knownExtensions) {
    final Set<PluginsAdvertiser.Plugin> plugins = knownExtensions.find(extension);
    if (plugins != null && !plugins.isEmpty()) {
      return createPanel(extension, plugins);
    }
    return null;
  }

  @Nullable
  private EditorNotificationPanel createPanel(final String extension, final Set<PluginsAdvertiser.Plugin> plugins) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    
    panel.setText("Plugins supporting " + extension + " files are found");
    final IdeaPluginDescriptor disabledPlugin = PluginsAdvertiser.getDisabledPlugin(plugins);
    if (disabledPlugin != null) {
      panel.createActionLabel("Enable " + disabledPlugin.getName() + " plugin", () -> {
        myEnabledExtensions.add(extension);
        myNotifications.updateAllNotifications();
        PluginsAdvertiser.enablePlugins(myProject, Collections.singletonList(disabledPlugin));
      });
    } else if (hasNonBundledPlugin(plugins)) {
      panel.createActionLabel("Install plugins", () -> {
        Set<String> pluginIds = new HashSet<>();
        for (PluginsAdvertiser.Plugin plugin : plugins) {
          pluginIds.add(plugin.myPluginId);
        }
        PluginsAdvertiser.installAndEnablePlugins(pluginIds, () -> {
          myEnabledExtensions.add(extension);
          myNotifications.updateAllNotifications();
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
        myNotifications.updateAllNotifications();
      });
    } else {
      return null;
    }
    panel.createActionLabel("Ignore extension", () -> {
      UnknownFeaturesCollector.getInstance(myProject).ignoreFeature(createExtensionFeature(extension));
      myNotifications.updateAllNotifications();
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
    return new UnknownFeature(FileTypeFactory.FILE_TYPE_FACTORY_EP.getName(), "File Type", extension);
  }
}
