// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;

public class PatchDownloadTask {
  @NotNull private final UpdateChannel myUpdatedChannel;
  @Nullable private final Collection<PluginDownloader> myUpdatedPlugins;
  @NotNull private final BuildInfo myNewBuild;
  @Nullable private final UpdateChain myPatches;
  @Nullable private final File myTestPatch;

  public PatchDownloadTask(@NotNull UpdateChannel channel,
                           @Nullable Collection<PluginDownloader> plugins,
                           @NotNull BuildInfo build,
                           @Nullable UpdateChain patches,
                           @Nullable File patch) {
    myUpdatedChannel = channel;
    myUpdatedPlugins = plugins;
    myNewBuild = build;
    myPatches = patches;
    myTestPatch = patch;
  }

  @Nullable
  public Task.Backgroundable invoke() {
    boolean updatePlugins = !ContainerUtil.isEmpty(myUpdatedPlugins);
    if (updatePlugins && !new PluginUpdateInfoDialog(myUpdatedPlugins).showAndGet()) {
      return null;  // update cancelled
    }

    return new Task.Backgroundable(null, IdeBundle.message("update.preparing"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String[] command;
        try {
          if (myTestPatch != null) {
            command = UpdateInstaller.preparePatchCommand(myTestPatch, indicator);
          }
          else {
            List<File> files = UpdateInstaller.downloadPatchChain(myPatches.getChain(), indicator);
            command = UpdateInstaller.preparePatchCommand(files, indicator);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(UpdateInstaller.class).warn(e);

          String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
          String downloadUrl = UpdateInfoPanel.downloadUrl(myNewBuild, myUpdatedChannel);
          String message = IdeBundle.message("update.downloading.patch.error", e.getMessage(), downloadUrl);
          UpdateChecker.getNotificationGroup().createNotification(
            title, message, NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER, "ide.patch.download.failed").notify(null);

          return;
        }

        if (updatePlugins) {
          UpdateInstaller.installPluginUpdates(myUpdatedPlugins, indicator);
        }

        if (ApplicationManager.getApplication().isRestartCapable()) {
          if (indicator.isShowing()) {
            restartLaterAndRunCommand(command);
          }
          else {
            String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
            String message = IdeBundle.message("update.ready.message");
            NotificationListener.Adapter listener = new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                restartLaterAndRunCommand(command);
              }
            };
            UpdateChecker.getNotificationGroup().createNotification(
              title, message, NotificationType.INFORMATION, listener, "ide.update.suggest.restart").notify(null);
          }
        }
        else {
          showPatchInstructions(command);
        }
      }
    };
  }

  private static void restartLaterAndRunCommand(String[] command) {
    IdeUpdateUsageTriggerCollector.trigger("dialog.update.started");
    PropertiesComponent.getInstance().setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, ApplicationInfo.getInstance().getBuild().asString());
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    application.invokeLater(() -> application.restart(ApplicationEx.EXIT_CONFIRMED | ApplicationEx.SAVE, command));
  }

  private static void showPatchInstructions(String[] command) {
    String product = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getFullProductName().replace(' ', '-'));
    String version = ApplicationInfo.getInstance().getFullVersion();
    File file = new File(SystemProperties.getUserHome(), product + "-" + version + "-patch." + (SystemInfo.isWindows ? "cmd" : "sh"));
    try {
      String cmdLine = StringUtil.join(CommandLineUtil.toCommandLine(Arrays.asList(command)), " ");
      @NonNls String text = (SystemInfo.isWindows ? "@echo off\n\n" : "#!/bin/sh\n\n") + cmdLine;
      FileUtil.writeToFile(file, text);
      FileUtil.setExecutable(file);
    }
    catch (Exception e) {
      Logger.getInstance(UpdateInstaller.class).error(e);
      return;
    }

    String title = IdeBundle.message("updates.dialog.title", ApplicationNamesInfo.getInstance().getFullProductName());
    String message = IdeBundle.message("update.apply.manually.message", file);
    IdeUpdateUsageTriggerCollector.trigger("dialog.manual.patch.prepared");
    ApplicationManager.getApplication().invokeLater(() -> Messages.showInfoMessage(message, title));
  }
}
