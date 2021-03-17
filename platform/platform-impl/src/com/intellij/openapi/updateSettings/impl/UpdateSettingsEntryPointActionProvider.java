// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SettingsEntryPointAction;
import com.intellij.ide.actions.SettingsEntryPointAction.IconState;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginStateListener;
import com.intellij.ide.plugins.PluginStateManager;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Alexander Lobas
 */
public class UpdateSettingsEntryPointActionProvider implements SettingsEntryPointAction.ActionProvider {
  private static CheckForUpdateResult myPlatformUpdateInfo;
  private static @Nullable Collection<IdeaPluginDescriptor> myIncompatiblePlugins;

  private static Collection<PluginDownloader> myUpdatedPlugins;
  private static Collection<IdeaPluginDescriptor> myCustomRepositoryPlugins;

  private static PluginUpdatesService myUpdatesService;
  private static PluginStateListener myPluginStateListener;

  private static boolean myEnableUpdateAction = true;

  public UpdateSettingsEntryPointActionProvider() {
    initPluginsListeners();
  }

  private static void initPluginsListeners() {
    if (myUpdatesService == null) {
      myUpdatesService = PluginUpdatesService.connectWithUpdates(descriptors -> {
        if (ContainerUtil.isEmpty(descriptors)) {
          newPluginUpdates(null, null, IconState.Current);
          return;
        }
        if (!UpdateSettings.getInstance().isPluginsCheckNeeded()) {
          return;
        }
        List<PluginDownloader> downloaders = new ArrayList<>();
        try {
          for (IdeaPluginDescriptor descriptor : descriptors) {
            if (!UpdateChecker.isIgnored(descriptor)) {
              downloaders.add(PluginDownloader.createDownloader(descriptor));
            }
          }
        }
        catch (IOException e) {
          PluginManagerCore.getLogger().error(e);
        }
        newPluginUpdates(downloaders.isEmpty() ? null : downloaders, null, IconState.Current);
      });
    }
    if (myPluginStateListener == null) {
      PluginStateManager.addStateListener(myPluginStateListener = new PluginStateListener() {
        @Override
        public void install(@NotNull IdeaPluginDescriptor descriptor) {
          removePluginsUpdate(Collections.singleton(descriptor));
        }
      });
    }
  }

  public static void newPlatformUpdate(@Nullable CheckForUpdateResult platformUpdateInfo,
                                       @Nullable List<PluginDownloader> updatedPlugins,
                                       @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins) {
    myPlatformUpdateInfo = platformUpdateInfo;
    myUpdatedPlugins = updatedPlugins;
    myIncompatiblePlugins = incompatiblePlugins;
    SettingsEntryPointAction.updateState(platformUpdateInfo != null ? IconState.ApplicationUpdate : IconState.Current);
  }

  public static void newPluginUpdates(@Nullable Collection<PluginDownloader> updatedPlugins,
                                      @Nullable Collection<IdeaPluginDescriptor> customRepositoryPlugins) {
    newPluginUpdates(updatedPlugins, customRepositoryPlugins,
                     updatedPlugins != null ? IconState.ApplicationComponentUpdate : IconState.Current);
  }

  private static void newPluginUpdates(@Nullable Collection<PluginDownloader> updatedPlugins,
                                       @Nullable Collection<IdeaPluginDescriptor> customRepositoryPlugins,
                                       @NotNull IconState state) {
    myUpdatedPlugins = updatedPlugins;
    myCustomRepositoryPlugins = customRepositoryPlugins;
    SettingsEntryPointAction.updateState(state);
  }

  public static void removePluginsUpdate(@NotNull Collection<IdeaPluginDescriptor> descriptors) {
    if (myUpdatedPlugins != null) {
      List<PluginDownloader> updatedPlugins =
        ContainerUtil.filter(myUpdatedPlugins, downloader -> {
          PluginId pluginId = downloader.getId();
          return ContainerUtil.find(descriptors, descriptor -> descriptor.getPluginId().equals(pluginId)) == null;
        });
      if (myUpdatedPlugins.size() != updatedPlugins.size()) {
        newPluginUpdates(updatedPlugins.isEmpty() ? null : updatedPlugins, myCustomRepositoryPlugins, IconState.Current);
      }
    }
  }

  private static void setEnableUpdateAction(boolean value) {
    myEnableUpdateAction = value;
  }

  @Override
  public @NotNull Collection<AnAction> getUpdateActions(@NotNull DataContext context) {
    List<AnAction> actions = new ArrayList<>();

    if (myPlatformUpdateInfo != null) {
      actions.add(new DumbAwareAction(IdeBundle.message("settings.entry.point.update.ide.action",
                                                        ApplicationNamesInfo.getInstance().getFullProductName(),
                                                        requireNonNull(myPlatformUpdateInfo.getNewBuild()).getVersion())) {
        {
          getTemplatePresentation().putClientProperty(ICON_KEY, IconState.ApplicationUpdate);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          boolean updateStarted = new UpdateInfoDialog(e.getProject(), requireNonNull(myPlatformUpdateInfo.getUpdatedChannel()),
                                                       myPlatformUpdateInfo.getNewBuild(), myPlatformUpdateInfo.getPatches(), true,
                                                       myUpdatedPlugins, myIncompatiblePlugins).showAndGet();
          if (updateStarted) {
            newPlatformUpdate(null, null, null);
          }
        }
      });
    }
    // todo[AL/RS] separate action for plugins compatible with both old and new builds
    else if (myUpdatedPlugins != null) {
      int size = myUpdatedPlugins.size();

      actions.add(new DumbAwareAction(size == 1
                                      ? IdeBundle.message("settings.entry.point.update.plugin.action",
                                                          myUpdatedPlugins.iterator().next().getPluginName())
                                      : IdeBundle.message("settings.entry.point.update.plugins.action", size)) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myEnableUpdateAction);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          PluginUpdateDialog dialog = new PluginUpdateDialog(e.getProject(), myUpdatedPlugins, myCustomRepositoryPlugins);
          dialog.setFinishCallback(() -> setEnableUpdateAction(true));
          setEnableUpdateAction(false);

          if (!dialog.showAndGet()) {
            setEnableUpdateAction(true);
          }
        }
      });
    }

    return actions;
  }

  @Override
  public int getOrder() {
    return -1;
  }
}