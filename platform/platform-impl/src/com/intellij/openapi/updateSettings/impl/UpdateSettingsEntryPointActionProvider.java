// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SettingsEntryPointAction;
import com.intellij.ide.actions.SettingsEntryPointAction.IconState;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * @author Alexander Lobas
 */
public class UpdateSettingsEntryPointActionProvider implements SettingsEntryPointAction.ActionProvider {
  private static final String NEXT_RUN_KEY_BUILD = "NextRunPlatformUpdateBuild";
  private static final String NEXT_RUN_KEY_VERSION = "NextRunPlatformUpdateVersion";

  private static String myNextRunPlatformUpdateVersion;
  private static CheckForUpdateResult myPlatformUpdateInfo;
  private static @Nullable Collection<IdeaPluginDescriptor> myIncompatiblePlugins;

  private static Collection<PluginDownloader> myUpdatedPlugins;
  private static Collection<PluginNode> myCustomRepositoryPlugins;

  private static PluginUpdatesService myUpdatesService;
  private static PluginStateListener myPluginStateListener;

  private static boolean myEnableUpdateAction = true;

  public static class LifecycleListener implements AppLifecycleListener {
    @Override
    public void appStarted() {
      preparePrevPlatformUpdate();
      initPluginsListeners();
    }
  }

  private static void preparePrevPlatformUpdate() {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    BuildNumber newBuildForUpdate = BuildNumber.fromString(properties.getValue(NEXT_RUN_KEY_BUILD));

    if (newBuildForUpdate != null) {
      if (newBuildForUpdate.compareTo(ApplicationInfo.getInstance().getBuild()) > 0) {
        myNextRunPlatformUpdateVersion = properties.getValue(NEXT_RUN_KEY_VERSION);

        if (myNextRunPlatformUpdateVersion != null) {
          SettingsEntryPointAction.updateState(IconState.ApplicationUpdate);
        }
        else {
          properties.unsetValue(NEXT_RUN_KEY_BUILD);
          properties.unsetValue(NEXT_RUN_KEY_VERSION);
        }
      }
      else {
        properties.unsetValue(NEXT_RUN_KEY_BUILD);
        properties.unsetValue(NEXT_RUN_KEY_VERSION);
      }
    }
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
          removePluginsUpdate(Set.of(descriptor.getPluginId()));
        }

        @Override
        public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
          install(descriptor);
        }
      });
    }
  }

  public static void newPlatformUpdate(@Nullable CheckForUpdateResult platformUpdateInfo,
                                       @Nullable List<PluginDownloader> updatedPlugins,
                                       @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins) {
    newPlatformUpdate(platformUpdateInfo, updatedPlugins, incompatiblePlugins, true);
  }

  private static void newPlatformUpdate(@Nullable CheckForUpdateResult platformUpdateInfo,
                                        @Nullable List<PluginDownloader> updatedPlugins,
                                        @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                        boolean updateMainAction) {
    myPlatformUpdateInfo = platformUpdateInfo;
    myUpdatedPlugins = updatedPlugins;
    myIncompatiblePlugins = incompatiblePlugins;
    myNextRunPlatformUpdateVersion = null;

    if (updateMainAction) {
      SettingsEntryPointAction.updateState(platformUpdateInfo != null ? IconState.ApplicationUpdate : IconState.Current);
    }

    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (platformUpdateInfo == null) {
      properties.unsetValue(NEXT_RUN_KEY_BUILD);
      properties.unsetValue(NEXT_RUN_KEY_VERSION);
    }
    else {
      BuildInfo build = requireNonNull(platformUpdateInfo.getNewBuild());
      properties.setValue(NEXT_RUN_KEY_BUILD, build.toString());
      properties.setValue(NEXT_RUN_KEY_VERSION, build.getVersion());
    }
  }

  public static void newPluginUpdates(@Nullable Collection<PluginDownloader> updatedPlugins,
                                      @Nullable Collection<PluginNode> customRepositoryPlugins) {
    newPluginUpdates(updatedPlugins,
                     customRepositoryPlugins,
                     updatedPlugins != null ? IconState.ApplicationComponentUpdate : IconState.Current);
  }

  private static void newPluginUpdates(@Nullable Collection<PluginDownloader> updatedPlugins,
                                       @Nullable Collection<PluginNode> customRepositoryPlugins,
                                       @NotNull IconState state) {
    myUpdatedPlugins = updatedPlugins;
    myCustomRepositoryPlugins = customRepositoryPlugins;
    SettingsEntryPointAction.updateState(state);
  }

  public static void removePluginsUpdate(@NotNull Set<PluginId> pluginIds) {
    if (myUpdatedPlugins != null) {
      List<PluginDownloader> updatedPlugins = ContainerUtil.filter(myUpdatedPlugins,
                                                                   downloader -> !pluginIds.contains(downloader.getId()));
      if (myUpdatedPlugins.size() != updatedPlugins.size()) {
        newPluginUpdates(updatedPlugins.isEmpty() ? null : updatedPlugins,
                         myCustomRepositoryPlugins,
                         IconState.Current);
      }
    }
  }

  private static void setEnableUpdateAction(boolean value) {
    myEnableUpdateAction = value;
  }

  @Override
  public @NotNull Collection<AnAction> getUpdateActions(@NotNull DataContext context) {
    List<AnAction> actions = new ArrayList<>();

    if (myNextRunPlatformUpdateVersion != null) {
      actions.add(new DumbAwareAction(IdeBundle.message("settings.entry.point.update.ide.action",
                                                        ApplicationNamesInfo.getInstance().getFullProductName(),
                                                        myNextRunPlatformUpdateVersion)) {
        {
          getTemplatePresentation().putClientProperty(ICON_KEY, IconState.ApplicationUpdate);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Ref<Triple<CheckForUpdateResult, List<PluginDownloader>, Collection<IdeaPluginDescriptor>>> refResult = new Ref<>();

          ProgressManager.getInstance().run(new Task.Modal(e.getProject(), IdeBundle.message("find.ide.update.title"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refResult.set(UpdateChecker.checkForPlatformUpdates(indicator));
            }
          });

          Triple<CheckForUpdateResult, List<PluginDownloader>, Collection<IdeaPluginDescriptor>> result = refResult.get();
          if (result == null) {
            return;
          }

          CheckForUpdateResult platformUpdateInfo = result.getFirst();
          if (platformUpdateInfo.getState() == UpdateStrategy.State.CONNECTION_ERROR) {
            Exception error = platformUpdateInfo.getError();
            Messages.showErrorDialog(e.getProject(),
                                     IdeBundle.message("updates.error.connection.failed", error == null ? "" : error.getMessage()),
                                     IdeBundle.message("find.ide.update.title"));
            return;
          }
          if (platformUpdateInfo.getState() == UpdateStrategy.State.NOTHING_LOADED ||
              platformUpdateInfo.getUpdatedChannel() == null || platformUpdateInfo.getNewBuild() == null) {
            Messages.showInfoMessage(e.getProject(), IdeBundle.message("updates.no.updates.notification"),
                                     IdeBundle.message("find.ide.update.title"));
            newPlatformUpdate(null, null, null);
            return;
          }

          newPlatformUpdate(platformUpdateInfo, result.getSecond(), result.getThird(), false);

          boolean updateStarted = new UpdateInfoDialog(e.getProject(), requireNonNull(myPlatformUpdateInfo.getUpdatedChannel()),
                                                       requireNonNull(myPlatformUpdateInfo.getNewBuild()),
                                                       myPlatformUpdateInfo.getPatches(), true, myUpdatedPlugins, myIncompatiblePlugins)
            .showAndGet();
          if (updateStarted) {
            newPlatformUpdate(null, null, null);
          }
        }
      });
    }
    else if (myPlatformUpdateInfo != null) {
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
}