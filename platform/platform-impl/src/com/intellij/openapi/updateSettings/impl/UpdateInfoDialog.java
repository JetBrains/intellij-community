// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;
import static com.intellij.openapi.util.Pair.pair;

/**
 * @author pti
 */
public final class UpdateInfoDialog extends AbstractUpdateDialog {
  private final Project myProject;
  private final UpdateChannel myUpdatedChannel;
  private final Collection<PluginDownloader> myUpdatedPlugins;
  private final BuildInfo myNewBuild;
  private final UpdateChain myPatches;
  private final boolean myWriteProtected;
  private final @Nullable Pair<@NlsContexts.Label String, Boolean> myLicenseInfo;
  private final File myTestPatch;
  private final AbstractAction myWhatsNewAction;

  public UpdateInfoDialog(@Nullable Project project,
                          @NotNull UpdateChannel channel,
                          @NotNull BuildInfo newBuild,
                          @Nullable UpdateChain patches,
                          boolean enableLink,
                          @Nullable Collection<PluginDownloader> updatedPlugins,
                          @Nullable Collection<? extends IdeaPluginDescriptor> incompatiblePlugins) {
    super(enableLink);
    myProject = project;
    myUpdatedChannel = channel;
    myUpdatedPlugins = updatedPlugins;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = myPatches != null && !SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath()));
    myLicenseInfo = getLicensingInfo(myUpdatedChannel, myNewBuild);
    myTestPatch = null;
    myWhatsNewAction = null;
    init();
    if (!ContainerUtil.isEmpty(incompatiblePlugins)) {
      String list = StringUtil.join(incompatiblePlugins, IdeaPluginDescriptor::getName, "<br/>");
      setErrorText(IdeBundle.message("updates.incompatible.plugins.found", incompatiblePlugins.size(), list));
    }
    IdeUpdateUsageTriggerCollector.triggerUpdateDialog(myPatches, ApplicationManager.getApplication().isRestartCapable());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  UpdateInfoDialog(@Nullable Project project, UpdateChannel channel, BuildInfo newBuild, UpdateChain patches, @Nullable File patchFile) {
    super(true);
    myProject = project;
    myUpdatedChannel = channel;
    myUpdatedPlugins = null;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = false;
    myLicenseInfo = getLicensingInfo(myUpdatedChannel, myNewBuild);
    myTestPatch = patchFile;
    myWhatsNewAction = project == null ? null : new AbstractAction("[T] What's New") {
      @Override
      public void actionPerformed(ActionEvent e) {
        WhatsNewAction.openWhatsNewFile(project, myNewBuild.getBlogPost(), myNewBuild.getMessage());
        close(OK_EXIT_CODE);
      }
    };
    init();
    setTitle("[TEST] " + getTitle());
  }

  private static @Nullable Pair<String, Boolean> getLicensingInfo(UpdateChannel channel, BuildInfo build) {
    LicensingFacade la = LicensingFacade.getInstance();
    if (la == null) return null;

    if (channel.getLicensing() == UpdateChannel.Licensing.EAP) {
      return pair(IdeBundle.message("updates.channel.bundled.key"), Boolean.FALSE);
    }

    Date releaseDate = build.getReleaseDate();
    if (releaseDate == null) return null;

    if (!la.isApplicableForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.paid.upgrade", channel.getEvalDays()), Boolean.TRUE);
    }
    if (la.isPerpetualForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.fallback.build"), Boolean.FALSE);
    }

    Date expiration = la.getLicenseExpirationDate();
    if (expiration != null) {
      return pair(IdeBundle.message("updates.interim.build", DateFormatUtil.formatAboutDialogDate(expiration)), Boolean.FALSE);
    }
    else {
      return null;
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    String licenseInfo = myLicenseInfo != null ? myLicenseInfo.first : null;
    boolean licenseWarn = myLicenseInfo != null && myLicenseInfo.second;
    return UpdateInfoPanel.create(myNewBuild, myPatches, myTestPatch, myWriteProtected, licenseInfo, licenseWarn, myEnableLink, myUpdatedChannel);
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected JComponent createSouthPanel() {
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
          String build = myNewBuild.getNumber().asStringWithoutProductCode();
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
    if (myPatches != null || myTestPatch != null) {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      String name = canRestart ? IdeBundle.message("updates.download.and.restart.button") : IdeBundle.message("updates.apply.manually.button");
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
      String downloadUrl = myNewBuild.getDownloadUrl();
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
  protected String getCancelButtonText() {
    return IdeBundle.message("updates.remind.later.button");
  }

  private void downloadPatchAndRestart() {
    if (!ContainerUtil.isEmpty(myUpdatedPlugins) && !new PluginUpdateDialog(myProject, myUpdatedPlugins, null).showAndGet()) {
      return;  // update cancelled
    }
    downloadPatchAndRestart(myNewBuild, myUpdatedChannel, myPatches, myTestPatch, myUpdatedPlugins, null);
  }

  public static void downloadPatchAndRestart(@NotNull BuildInfo newBuild,
                                             @NotNull UpdateChannel updatedChannel,
                                             @NotNull UpdateChain patches,
                                             @Nullable File testPatch,
                                             @Nullable Collection<PluginDownloader> updatedPlugins,
                                             @Nullable ActionCallback callback) {
    new Task.Backgroundable(null, IdeBundle.message("update.preparing"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String[] command;
        try {
          if (testPatch != null) {
            command = UpdateInstaller.preparePatchCommand(testPatch, indicator);
          }
          else {
            List<File> files = UpdateInstaller.downloadPatchChain(patches.getChain(), indicator);
            command = UpdateInstaller.preparePatchCommand(files, indicator);
          }
        }
        catch (ProcessCanceledException e) {
          if (callback != null) {
            callback.setRejected();
          }
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(UpdateInstaller.class).warn(e);

          if (callback != null) {
            callback.setRejected();
          }

          String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
          String downloadUrl = UpdateInfoPanel.downloadUrl(newBuild, updatedChannel);
          String message = IdeBundle.message("update.downloading.patch.error", e.getMessage(), downloadUrl);
          UpdateChecker.getNotificationGroup().createNotification(
            title, message, NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER, "ide.patch.download.failed").notify(null);

          return;
        }

        if (!ContainerUtil.isEmpty(updatedPlugins)) {
          UpdateInstaller.installPluginUpdates(updatedPlugins, indicator);
        }

        if (callback != null) {
          callback.setDone();
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
    }.queue();
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
