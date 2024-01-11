// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.ide.actions.SettingsEntryPointAction.*;

/**
 * @author Alexander Lobas
 */
final class UpdateSettingsEntryPointActionProvider implements ActionProvider {
  private static final String NEXT_RUN_KEY_BUILD = "NextRunPlatformUpdateBuild";
  private static final String NEXT_RUN_KEY_VERSION = "NextRunPlatformUpdateVersion";

  private static boolean myNewPlatformUpdate;
  private static @Nullable String myNextRunPlatformUpdateVersion;
  private static @Nullable PlatformUpdates.Loaded myPlatformUpdateInfo;
  private static @Nullable Collection<? extends IdeaPluginDescriptor> myIncompatiblePlugins;

  private static Set<String> myAlreadyShownPluginUpdates;
  private static @Nullable Collection<PluginDownloader> myUpdatedPlugins;
  private static @Nullable Collection<PluginNode> myCustomRepositoryPlugins;

  private static PluginUpdatesService myUpdatesService;
  private static PluginStateListener myPluginStateListener;

  private static boolean myEnableUpdateAction = true;

  static final class LifecycleListener implements AppLifecycleListener {
    @Override
    public void appStarted() {
      preparePrevPlatformUpdate();
      initPluginsListeners();
    }
  }

  private static void preparePrevPlatformUpdate() {
    if (!UpdateSettings.getInstance().isCheckNeeded()) {
      return;
    }

    PropertiesComponent properties = PropertiesComponent.getInstance();
    BuildNumber newBuildForUpdate;
    try {
      newBuildForUpdate = BuildNumber.fromString(properties.getValue(NEXT_RUN_KEY_BUILD));
    }
    catch (Exception ignore) {
      return;
    }

    if (newBuildForUpdate != null) {
      if (newBuildForUpdate.compareTo(ApplicationInfo.getInstance().getBuild()) > 0) {
        myNextRunPlatformUpdateVersion = properties.getValue(NEXT_RUN_KEY_VERSION);

        if (myNextRunPlatformUpdateVersion != null) {
          myNewPlatformUpdate = true;
          updateState();
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
          newUpdatedPlugins(null);
          myCustomRepositoryPlugins = null;
          return;
        }
        if (!UpdateSettings.getInstance().isPluginsCheckNeeded()) {
          return;
        }
        List<PluginDownloader> downloaders = new ArrayList<>();
        try {
          for (IdeaPluginDescriptor descriptor : descriptors) {
            if (!UpdateChecker.isIgnored(descriptor) && /* IDEA-273418 */ !PluginManagerCore.isDisabled(descriptor.getPluginId())) {
              downloaders.add(PluginDownloader.createDownloader(descriptor));
            }
          }
        }
        catch (IOException e) {
          PluginManagerCore.getLogger().error(e);
        }
        newUpdatedPlugins(downloaders);
        myCustomRepositoryPlugins = null;
      });
    }
    if (myPluginStateListener == null) {
      PluginStateManager.addStateListener(myPluginStateListener = new PluginStateListener() {
        @Override
        public void install(@NotNull IdeaPluginDescriptor descriptor) {
          removePluginsUpdate(List.of(descriptor));
        }

        @Override
        public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
          install(descriptor);
        }
      });
    }
  }

  private static void newPlatformUpdate() {
    setPlatformUpdateInfo(null);
    newPlatformUpdate(null, null, (String)null);
    updateState();
  }

  public static void newPlatformUpdate(@NotNull PlatformUpdates.Loaded platformUpdateInfo,
                                       @NotNull List<PluginDownloader> updatedPlugins,
                                       @NotNull Collection<? extends IdeaPluginDescriptor> incompatiblePlugins) {
    UpdateSettings settings = UpdateSettings.getInstance();
    if (settings.isCheckNeeded()) {
      setPlatformUpdateInfo(platformUpdateInfo);
    }
    else {
      setPlatformUpdateInfo(null);
    }
    if (settings.isPluginsCheckNeeded()) {
      newPlatformUpdate(updatedPlugins, incompatiblePlugins, null);
    }
    else {
      newPlatformUpdate(null, null, (String)null);
    }
    updateState();
  }

  private static void setPlatformUpdateInfo(@Nullable PlatformUpdates.Loaded platformUpdateInfo) {
    myPlatformUpdateInfo = platformUpdateInfo;
    myNewPlatformUpdate = platformUpdateInfo != null;

    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (platformUpdateInfo == null) {
      properties.unsetValue(NEXT_RUN_KEY_BUILD);
      properties.unsetValue(NEXT_RUN_KEY_VERSION);
    }
    else {
      BuildInfo build = platformUpdateInfo.getNewBuild();
      properties.setValue(NEXT_RUN_KEY_BUILD, build.getNumber().toString());
      properties.setValue(NEXT_RUN_KEY_VERSION, build.getVersion());
    }
  }

  private static void newPlatformUpdate(@Nullable List<PluginDownloader> updatedPlugins,
                                        @Nullable Collection<? extends IdeaPluginDescriptor> incompatiblePlugins,
                                        @Nullable String nextRunPlatformUpdateVersion) {
    myUpdatedPlugins = updatedPlugins;
    myIncompatiblePlugins = incompatiblePlugins;
    myNextRunPlatformUpdateVersion = nextRunPlatformUpdateVersion;
  }

  public static void newPluginUpdates(@NotNull Collection<PluginDownloader> updatedPlugins,
                                      @NotNull Collection<PluginNode> customRepositoryPlugins) {
    if (UpdateSettings.getInstance().isPluginsCheckNeeded()) {
      myUpdatedPlugins = updatedPlugins;
      myCustomRepositoryPlugins = customRepositoryPlugins;
    }
    else {
      myUpdatedPlugins = null;
    }
    updateState();
  }

  public static @Nullable Collection<PluginDownloader> getPendingUpdates() {
    return myUpdatedPlugins;
  }

  private static void newUpdatedPlugins(@Nullable Collection<PluginDownloader> updatedPlugins) {
    myUpdatedPlugins = ContainerUtil.isEmpty(updatedPlugins) ? null : updatedPlugins;
    updateState();
  }

  static void removePluginsUpdate(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
    if (myAlreadyShownPluginUpdates != null) {
      myAlreadyShownPluginUpdates.removeIf(name -> descriptors.stream().anyMatch(descriptor -> name.equals(descriptor.getName())));
    }
    if (myUpdatedPlugins != null) {
      Set<PluginId> pluginIds = ContainerUtil.map2Set(descriptors,
                                                      IdeaPluginDescriptor::getPluginId);
      List<PluginDownloader> updatedPlugins = ContainerUtil.filter(myUpdatedPlugins,
                                                                   downloader -> !pluginIds.contains(downloader.getId()));
      if (myUpdatedPlugins.size() != updatedPlugins.size()) {
        newUpdatedPlugins(updatedPlugins);
      }
    }
  }

  private static boolean isAlreadyShownPluginUpdates() {
    return myUpdatedPlugins == null || ContainerUtil.isEmpty(myAlreadyShownPluginUpdates) ||
           myUpdatedPlugins.stream().anyMatch(plugin -> !myAlreadyShownPluginUpdates.contains(plugin.getPluginName()));
  }

  private static void updateAlreadyShownPluginUpdates() {
    if (myUpdatedPlugins != null) {
      if (myAlreadyShownPluginUpdates == null) {
        myAlreadyShownPluginUpdates = new HashSet<>();
      }
      myUpdatedPlugins.forEach(plugin -> myAlreadyShownPluginUpdates.add(plugin.getPluginName()));
    }
  }

  private static void setEnableUpdateAction(boolean value) {
    myEnableUpdateAction = value;
  }

  @Override
  public @NotNull Collection<UpdateAction> getUpdateActions(@NotNull DataContext context) {
    Collection<UpdateAction> actions = new ArrayList<>();

    if (myNextRunPlatformUpdateVersion != null) {
      actions.add(new IdeUpdateAction(myNextRunPlatformUpdateVersion) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Project project = e.getProject();
          Pair<PlatformUpdates, InternalPluginResults> result = ProgressManager.getInstance()
            .run(new Task.WithResult<>(project,
                                       IdeBundle.message("find.ide.update.title"),
                                       true) {

              @Override
              protected @NotNull Pair<@NotNull PlatformUpdates, @Nullable InternalPluginResults> compute(@NotNull ProgressIndicator indicator) {
                PlatformUpdates platformUpdates = UpdateChecker.getPlatformUpdates(UpdateSettings.getInstance(), indicator);

                InternalPluginResults pluginResults = platformUpdates instanceof PlatformUpdates.Loaded ?
                                                      getInternalPluginUpdates((PlatformUpdates.Loaded)platformUpdates, indicator) :
                                                      null;
                return Pair.create(platformUpdates,
                                   pluginResults);
              }

              private static @NotNull InternalPluginResults getInternalPluginUpdates(@NotNull PlatformUpdates.Loaded loadedResult,
                                                                                     @NotNull ProgressIndicator indicator) {
                return UpdateChecker.getInternalPluginUpdates(loadedResult.getNewBuild().getApiVersion(),
                                                              indicator);
              }
            });

          PlatformUpdates platformUpdateInfo = result.getFirst();
          InternalPluginResults pluginResults = result.getSecond();
          if (platformUpdateInfo instanceof PlatformUpdates.Loaded &&
              pluginResults != null) {
            setPlatformUpdateInfo((PlatformUpdates.Loaded)platformUpdateInfo);
            newPlatformUpdate(pluginResults.getPluginUpdates().getAll(),
                              pluginResults.getPluginNods(),
                              null);

            super.actionPerformed(e);
          }
          else {
            if (platformUpdateInfo instanceof PlatformUpdates.ConnectionError) {
              String errorMessage = ((PlatformUpdates.ConnectionError)platformUpdateInfo).getError().getMessage();
              Messages.showErrorDialog(project,
                                       IdeBundle.message("updates.error.connection.failed", errorMessage),
                                       IdeBundle.message("find.ide.update.title"));
            }
            else {
              Messages.showInfoMessage(project,
                                       IdeBundle.message("updates.no.updates.notification"),
                                       IdeBundle.message("find.ide.update.title"));
              newPlatformUpdate();
            }
          }
        }
      });
    }
    else if (myPlatformUpdateInfo != null) {
      actions.add(new IdeUpdateAction(myPlatformUpdateInfo.getNewBuild().getVersion()));
    }
    // todo[AL/RS] separate action for plugins compatible with both old and new builds
    else if (myUpdatedPlugins != null && !myUpdatedPlugins.isEmpty()) {
      int size = myUpdatedPlugins.size();

      actions.add(new UpdateAction(size == 1
                                   ? IdeBundle.message("settings.entry.point.update.plugin.action",
                                                       myUpdatedPlugins.iterator().next().getPluginName())
                                   : IdeBundle.message("settings.entry.point.update.plugins.action", size)) {
        @Override
        public boolean isNewAction() {
          return isAlreadyShownPluginUpdates();
        }

        @Override
        public void markAsRead() {
          updateAlreadyShownPluginUpdates();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myEnableUpdateAction);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
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

  private static class IdeUpdateAction extends UpdateAction {
    protected IdeUpdateAction(@NotNull String version) {
      super(IdeBundle.message("settings.entry.point.update.ide.action", ApplicationNamesInfo.getInstance().getFullProductName(), version));
    }

    @Override
    public boolean isIdeUpdate() {
      return true;
    }

    @Override
    public boolean isNewAction() {
      return myNewPlatformUpdate;
    }

    @Override
    public void markAsRead() {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      myNewPlatformUpdate = false;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      UpdateInfoDialog dialog = new UpdateInfoDialog(e.getProject(), Objects.requireNonNull(myPlatformUpdateInfo),
                                                     true, myUpdatedPlugins, myIncompatiblePlugins);
      if (dialog.showAndGet()) {
        newPlatformUpdate();
      }
    }
  }
}