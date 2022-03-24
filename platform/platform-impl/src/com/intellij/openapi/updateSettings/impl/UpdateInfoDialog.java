// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerService.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;
import static com.intellij.openapi.util.Pair.pair;

/**
 * @author pti
 */
public final class UpdateInfoDialog extends AbstractUpdateDialog {

  private final @Nullable Project myProject;
  private final @NotNull PlatformUpdates.Loaded myLoadedResult;
  private final @Nullable Collection<PluginDownloader> myUpdatedPlugins;
  private final boolean myWriteProtected;
  private final @Nullable Pair<@NlsContexts.Label String, Boolean> myLicenseInfo;
  private final @Nullable File myTestPatch;
  private final @Nullable AbstractAction myWhatsNewAction;

  public UpdateInfoDialog(@Nullable Project project,
                          @NotNull PlatformUpdates.Loaded loadedResult,
                          boolean enableLink,
                          @Nullable Collection<PluginDownloader> updatedPlugins,
                          @Nullable Collection<? extends IdeaPluginDescriptor> incompatiblePlugins) {
    super(project, enableLink);
    myProject = project;
    myLoadedResult = loadedResult;
    myUpdatedPlugins = updatedPlugins;
    UpdateChain patches = myLoadedResult.getPatches();
    myWriteProtected = patches != null && !SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath()));
    myLicenseInfo = getLicensingInfo(myLoadedResult);
    myTestPatch = null;
    myWhatsNewAction = null;
    init();
    if (!ContainerUtil.isEmpty(incompatiblePlugins)) {
      String names = incompatiblePlugins.stream()
        .map(IdeaPluginDescriptor::getName)
        .collect(Collectors.joining("<br/>"));
      setErrorText(IdeBundle.message("updates.incompatible.plugins.found", incompatiblePlugins.size(), names));
    }
    IdeUpdateUsageTriggerCollector.triggerUpdateDialog(patches, ApplicationManager.getApplication().isRestartCapable());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  UpdateInfoDialog(@Nullable Project project,
                   @NotNull PlatformUpdates.Loaded loadedResult,
                   @Nullable File patchFile) {
    super(true);
    myProject = project;
    myLoadedResult = loadedResult;
    myUpdatedPlugins = null;
    myWriteProtected = false;
    myLicenseInfo = getLicensingInfo(myLoadedResult);
    myTestPatch = patchFile;
    String whatsNewUrl = myLoadedResult.getNewBuild().getBlogPost();
    myWhatsNewAction = project == null || whatsNewUrl == null ? null : new AbstractAction("[T] What's New") {
      @Override
      public void actionPerformed(ActionEvent e) {
        WhatsNewAction.openWhatsNewPage(project, whatsNewUrl);
        close(OK_EXIT_CODE);
      }
    };
    init();
    setTitle("[TEST] " + getTitle());
  }

  private static @Nullable Pair<@NlsContexts.Label String, Boolean> getLicensingInfo(@NotNull PlatformUpdates.Loaded result) {
    LicensingFacade la = LicensingFacade.getInstance();
    if (la == null) return null;

    UpdateChannel channel = result.getUpdatedChannel();
    if (channel.getLicensing() == UpdateChannel.Licensing.EAP) {
      return pair(IdeBundle.message("updates.channel.bundled.key"), Boolean.FALSE);
    }

    Date releaseDate = result.getNewBuild().getReleaseDate();
    if (releaseDate == null) return null;

    if (!la.isApplicableForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.paid.upgrade", channel.getEvalDays()), Boolean.TRUE);
    }
    if (la.isPerpetualForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.fallback.build"), Boolean.FALSE);
    }

    Date expiration = la.getLicenseExpirationDate();
    if (expiration != null) {
      return pair(IdeBundle.message("updates.interim.build", NlsMessages.formatDateLong(expiration)), Boolean.FALSE);
    }
    else {
      return null;
    }
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return UpdateInfoPanel.create(myLoadedResult.getNewBuild(),
                                  myLoadedResult.getPatches(),
                                  myTestPatch,
                                  myWriteProtected,
                                  myLicenseInfo != null ? myLicenseInfo.first : null,
                                  myLicenseInfo != null && myLicenseInfo.second,
                                  myEnableLink,
                                  myLoadedResult.getUpdatedChannel());
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected @NotNull JComponent createSouthPanel() {
    JComponent component = super.createSouthPanel();
    component.setBorder(JBUI.Borders.empty(8));
    return component;
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    return new Action[]{
      new AbstractAction(IdeBundle.message("updates.ignore.update.button")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          String build = myLoadedResult.getNewBuild().getNumber().asStringWithoutProductCode();
          UpdateSettings.getInstance().getIgnoredBuildNumbers().add(build);
          doCancelAction();
        }
      }
    };
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getCancelAction());

    AbstractAction updateButton = null;
    if (myLoadedResult.getPatches() != null || myTestPatch != null) {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      String name =
        canRestart ? IdeBundle.message("updates.download.and.restart.button") : IdeBundle.message("updates.apply.manually.button");
      updateButton = new AbstractAction(name) {
        @Override
        public void actionPerformed(ActionEvent e) {
          close(OK_EXIT_CODE);
          downloadPatchAndRestart();
        }
      };
      updateButton.setEnabled(!myWriteProtected);
    }
    else {
      String downloadUrl = myLoadedResult.getNewBuild().getDownloadUrl();
      if (downloadUrl != null) {
        updateButton = new AbstractAction(IdeBundle.message("updates.download.button")) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(OK_EXIT_CODE);
            BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(downloadUrl));
          }
        };
      }
    }

    if (updateButton != null) {
      updateButton.putValue(DEFAULT_ACTION, Boolean.TRUE);
      actions.add(updateButton);
    }

    if (myWhatsNewAction != null) {
      actions.add(myWhatsNewAction);
    }

    return actions.toArray(new Action[0]);
  }

  @Override
  protected @NotNull String getCancelButtonText() {
    return IdeBundle.message("updates.remind.later.button");
  }

  private void downloadPatchAndRestart() {
    if (!ContainerUtil.isEmpty(myUpdatedPlugins) && !new PluginUpdateDialog(myProject, myUpdatedPlugins).showAndGet()) {
      return;  // update cancelled
    }

    new Task.Backgroundable(null, IdeBundle.message("update.preparing"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String[] command;
        try {
          if (myTestPatch != null) {
            command = UpdateInstaller.preparePatchCommand(List.of(myTestPatch), indicator);
          }
          else {
            List<File> files = UpdateInstaller.downloadPatchChain(myLoadedResult.getPatches().getChain(), indicator);
            command = UpdateInstaller.preparePatchCommand(files, indicator);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(UpdateInstaller.class).warn(e);

          String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
          String downloadUrl = UpdateInfoPanel.downloadUrl(myLoadedResult.getNewBuild(), myLoadedResult.getUpdatedChannel());
          String message = IdeBundle.message("update.downloading.patch.error", e.getMessage(), downloadUrl);
          UpdateChecker.getNotificationGroupForIdeUpdateResults()
            .createNotification(title, message, NotificationType.ERROR)
            .setListener(NotificationListener.URL_OPENING_LISTENER)
            .setDisplayId("ide.patch.download.failed")
            .notify(null);

          return;
        }

        if (!ContainerUtil.isEmpty(myUpdatedPlugins)) {
          UpdateInstaller.installPluginUpdates(myUpdatedPlugins, indicator);
        }

        if (ApplicationManager.getApplication().isRestartCapable()) {
          if (indicator.isShowing()) {
            restartLaterAndRunCommand(command);
          }
          else {
            String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
            String message = IdeBundle.message("update.ready.message");
            UpdateChecker.getNotificationGroupForIdeUpdateResults()
              .createNotification(title, message, NotificationType.INFORMATION)
              .setListener(new NotificationListener.Adapter() {
                @Override
                protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                  restartLaterAndRunCommand(command);
                }
              })
              .setDisplayId("ide.update.suggest.restart")
              .notify(null);
          }
        }
        else {
          showPatchInstructions(command);
        }
      }
    }.queue();
  }

  private static void restartLaterAndRunCommand(String[] command) {
    IdeUpdateUsageTriggerCollector.UPDATE_STARTED.log();
    PropertiesComponent.getInstance().setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, ApplicationInfo.getInstance().getBuild().asString());
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    application.invokeLater(() -> application.restart(ApplicationEx.EXIT_CONFIRMED | ApplicationEx.SAVE, command));
  }

  private static void showPatchInstructions(String[] command) {
    String product = ApplicationNamesInfo.getInstance().getFullProductName().replace(' ', '-').toLowerCase(Locale.ENGLISH);
    String version = ApplicationInfo.getInstance().getFullVersion();
    File file = new File(SystemProperties.getUserHome(), product + "-" + version + "-patch." + (SystemInfo.isWindows ? "cmd" : "sh"));
    try {
      String cmdLine = String.join(" ", CommandLineUtil.toCommandLine(Arrays.asList(command)));
      String text = (SystemInfo.isWindows ? "@echo off\n\n" : "#!/bin/sh\n\n") + cmdLine;
      FileUtil.writeToFile(file, text);
      FileUtil.setExecutable(file);
    }
    catch (Exception e) {
      Logger.getInstance(UpdateInstaller.class).error(e);
      return;
    }

    String title = IdeBundle.message("updates.dialog.title", ApplicationNamesInfo.getInstance().getFullProductName());
    String message = IdeBundle.message("update.apply.manually.message", file);
    IdeUpdateUsageTriggerCollector.MANUAL_PATCH_PREPARED.log();
    ApplicationManager.getApplication().invokeLater(() -> Messages.showInfoMessage(message, title));
  }
}
