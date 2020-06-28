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
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;
import static com.intellij.openapi.util.Pair.pair;

/**
 * @author pti
 */
final class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChannel myUpdatedChannel;
  private final Collection<PluginDownloader> myUpdatedPlugins;
  private final BuildInfo myNewBuild;
  private final UpdateChain myPatches;
  private final boolean myWriteProtected;
  private final Pair<String, Color> myLicenseInfo;
  private final File myTestPatch;

  private AbstractAction myWhatsNewAction;

  UpdateInfoDialog(@NotNull UpdateChannel channel,
                   @NotNull BuildInfo newBuild,
                   @Nullable UpdateChain patches,
                   boolean enableLink,
                   @Nullable Collection<PluginDownloader> updatedPlugins,
                   @Nullable Collection<? extends IdeaPluginDescriptor> incompatiblePlugins) {
    super(enableLink);
    myUpdatedChannel = channel;
    myUpdatedPlugins = updatedPlugins;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = myPatches != null && !SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath()));
    myLicenseInfo = initLicensingInfo(myUpdatedChannel, myNewBuild);
    myTestPatch = null;
    init();

    if (!ContainerUtil.isEmpty(incompatiblePlugins)) {
      String list = StringUtil.join(incompatiblePlugins, IdeaPluginDescriptor::getName, "<br/>");
      setErrorText(IdeBundle.message("updates.incompatible.plugins.found", incompatiblePlugins.size(), list));
    }
    IdeUpdateUsageTriggerCollector.triggerUpdateDialog(myPatches, ApplicationManager.getApplication().isRestartCapable());
  }

  UpdateInfoDialog(@Nullable Project project, UpdateChannel channel, BuildInfo newBuild, UpdateChain patches, @Nullable File patchFile) {
    super(true);
    myUpdatedChannel = channel;
    myUpdatedPlugins = null;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = false;
    myLicenseInfo = initLicensingInfo(myUpdatedChannel, myNewBuild);
    myTestPatch = patchFile;
    if (project != null) {
      myWhatsNewAction = new AbstractAction(IdeBundle.message("button.what.s.new")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          String title = IdeBundle.message("update.whats.new.file.name", ApplicationInfo.getInstance().getFullVersion());
          HTMLEditorProvider.Companion.openEditor(project, title, myNewBuild.getBlogPost() + WhatsNewAction.getEmbeddedSuffix(), null, myNewBuild.getMessage());
          close(OK_EXIT_CODE);
        }
      };
    }
    init();
    //noinspection HardCodedStringLiteral
    setTitle("[TEST] " + getTitle());
  }

  private static Pair<String, Color> initLicensingInfo(UpdateChannel channel, BuildInfo build) {
    final LicensingFacade la = LicensingFacade.getInstance();
    if (la == null) return null;

    if (channel.getLicensing().equals(UpdateChannel.LICENSING_EAP)) {
      return pair(IdeBundle.message("updates.channel.bundled.key"), null);
    }

    Date releaseDate = build.getReleaseDate();
    if (releaseDate == null) {
      return null;
    }

    if (!la.isApplicableForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.paid.upgrade", channel.getEvalDays()), JBColor.RED);
    }
    if (la.isPerpetualForProduct(releaseDate)) {
      return pair(IdeBundle.message("updates.fallback.build"), null);
    }

    Date expiration = la.getLicenseExpirationDate();
    if (expiration != null) {
      return pair(IdeBundle.message("updates.interim.build", DateFormatUtil.formatAboutDialogDate(expiration)), null);
    }
    else {
      return null;
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return UpdateInfoPanelUI.INSTANCE
      .createPanel(myNewBuild, myPatches, myTestPatch, myWriteProtected, myLicenseInfo, myEnableLink, myUpdatedChannel);
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
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
    return ContainerUtil.ar(new AbstractAction(IdeBundle.message("updates.ignore.update.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        String build = myNewBuild.getNumber().asStringWithoutProductCode();
        UpdateSettings.getInstance().getIgnoredBuildNumbers().add(build);
        doCancelAction();
      }
    });
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getCancelAction());

    AbstractAction updateButton;
    if (myPatches != null || myTestPatch != null) {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      updateButton =
        new AbstractAction(IdeBundle.message(canRestart ? "updates.download.and.restart.button" : "updates.apply.manually.button")) {
          {
            setEnabled(!myWriteProtected);
          }

          @Override
          public void actionPerformed(ActionEvent e) {
            close(OK_EXIT_CODE);
            downloadPatchAndRestart();
          }
        };
    }
    else {
      updateButton = myNewBuild.getButtons().stream()
        .filter(info -> info.isDownload())
        .findAny()
        .map(info -> new AbstractAction(info.getName()) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(OK_EXIT_CODE);
            BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(info.getUrl()));
          }
        })
        .orElse(null);
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
    boolean updatePlugins = !ContainerUtil.isEmpty(myUpdatedPlugins);
    if (updatePlugins && !new PluginUpdateInfoDialog(myUpdatedPlugins).showAndGet()) {
      return;  // update cancelled
    }

    new Task.Backgroundable(null, IdeBundle.message("update.preparing"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String[] command;
        try {
          if (myPatches != null) {
            List<File> files = UpdateInstaller.downloadPatchChain(myPatches.getChain(), indicator);
            command = UpdateInstaller.preparePatchCommand(files, indicator);
          }
          else {
            command = UpdateInstaller.preparePatchCommand(myTestPatch, indicator);
          }
        }
        catch (ProcessCanceledException e) { throw e; }
        catch (Exception e) {
          Logger.getInstance(UpdateInstaller.class).warn(e);

          String title = IdeBundle.message("updates.error.connection.title");
          String message = IdeBundle.message("update.downloading.patch.error", e.getMessage(), downloadUrl());
          UpdateChecker.getNotificationGroup().createNotification(title, message, NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER, "ide.patch.download.failed").notify(null);

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
            String title = IdeBundle.message("update.notifications.title");
            String message = IdeBundle.message("update.ready.message");
            UpdateChecker.getNotificationGroup().createNotification(title, message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                restartLaterAndRunCommand(command);
              }
            }, "ide.update.suggest.restart").notify(null);
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

  private String downloadUrl() {
    String url = myNewBuild.getDownloadUrl();
    if (url == null) url = myNewBuild.getBlogPost();
    if (url == null) url = myUpdatedChannel.getUrl();
    if (url == null) url = "https://www.jetbrains.com";
    return IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url);
  }

  private static void showPatchInstructions(String[] command) {
    String product = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getFullProductName().replace(' ', '-'));
    String version = ApplicationInfo.getInstance().getFullVersion();
    File file = new File(SystemProperties.getUserHome(), product + "-" + version + "-patch." + (SystemInfo.isWindows ? "cmd" : "sh"));
    try {
      String text = (SystemInfo.isWindows ? "@echo off\n\n" : "#!/bin/sh\n\n") +
                    StringUtil.join(CommandLineUtil.toCommandLine(Arrays.asList(command)), " ");
      FileUtil.writeToFile(file, text);
      FileUtil.setExecutable(file);
    }
    catch (Exception e) {
      Logger.getInstance(UpdateInstaller.class).error(e);
      return;
    }

    String title = IdeBundle.message("update.notifications.title"), message = IdeBundle.message("update.apply.manually.message", file);
    IdeUpdateUsageTriggerCollector.trigger( "dialog.manual.patch.prepared");
    ApplicationManager.getApplication().invokeLater(() -> Messages.showInfoMessage(message, title));
  }
}