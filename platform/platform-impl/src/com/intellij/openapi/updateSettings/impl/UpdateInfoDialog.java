// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
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

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerService.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;
import static com.intellij.openapi.util.Pair.pair;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * @author pti
 */
class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChannel myUpdatedChannel;
  private final Collection<? extends PluginDownloader> myUpdatedPlugins;
  private final BuildInfo myNewBuild;
  private final UpdateChain myPatches;
  private final boolean myWriteProtected;
  private final Pair<String, Color> myLicenseInfo;
  private final File myTestPatch;

  UpdateInfoDialog(@NotNull UpdateChannel channel,
                   @NotNull BuildInfo newBuild,
                   @Nullable UpdateChain patches,
                   boolean enableLink,
                   @Nullable Collection<? extends PluginDownloader> updatedPlugins,
                   @Nullable Collection<? extends IdeaPluginDescriptor> incompatiblePlugins) {
    super(enableLink);
    myUpdatedChannel = channel;
    myUpdatedPlugins = updatedPlugins;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = myPatches != null && !SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath()));
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    myLicenseInfo = initLicensingInfo(myUpdatedChannel, myNewBuild);
    myTestPatch = null;
    init();

    if (!ContainerUtil.isEmpty(incompatiblePlugins)) {
      String list = StringUtil.join(incompatiblePlugins, IdeaPluginDescriptor::getName, "<br/>");
      setErrorText(IdeBundle.message("updates.incompatible.plugins.found", incompatiblePlugins.size(), list));
    }

    IdeUpdateUsageTriggerCollector.trigger( "dialog.shown");
    if (myPatches == null) {
      IdeUpdateUsageTriggerCollector.trigger( "dialog.shown.no.patch");
    }
    else if (!ApplicationManager.getApplication().isRestartCapable()) {
      IdeUpdateUsageTriggerCollector.trigger( "dialog.shown.manual.patch");
    }
  }

  UpdateInfoDialog(UpdateChannel channel, BuildInfo newBuild, UpdateChain patches, @Nullable File patchFile) {
    super(true);
    myUpdatedChannel = channel;
    myUpdatedPlugins = null;
    myNewBuild = newBuild;
    myPatches = patches;
    myWriteProtected = false;
    myLicenseInfo = null;
    myTestPatch = patchFile;
    init();
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
    return new UpdateInfoPanel().myPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = new ArrayList<>();

    if (myPatches != null || myTestPatch != null) {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      actions.add(new AbstractAction(IdeBundle.message(canRestart ? "updates.download.and.restart.button" : "updates.apply.manually.button")) {
        {
          setEnabled(!myWriteProtected);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          close(OK_EXIT_CODE);
          downloadPatchAndRestart();
        }
      });
    }

    List<ButtonInfo> buttons = myNewBuild.getButtons();
    for (ButtonInfo info : buttons) {
      if (!info.isDownload() || myPatches == null && myTestPatch == null) {
        actions.add(new ButtonAction(info));
      }
    }

    actions.add(new AbstractAction(IdeBundle.message("updates.ignore.update.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        String build = myNewBuild.getNumber().asStringWithoutProductCode();
        UpdateSettings.getInstance().getIgnoredBuildNumbers().add(build);
        doCancelAction();
      }
    });

    actions.add(getCancelAction());

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

    new Task.Backgroundable(null, IdeBundle.message("update.notifications.title"), true, PerformInBackgroundOption.DEAF) {
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
          UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER).notify(null);

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
            UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                restartLaterAndRunCommand(command);
              }
            }).notify(null);
          }
        }
        else {
          showPatchInstructions(command);
        }
      }
    }.queue();
  }

  private static void restartLaterAndRunCommand(String[] command) {
    IdeUpdateUsageTriggerCollector.trigger( "dialog.update.started");
    PropertiesComponent.getInstance().setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, ApplicationInfo.getInstance().getBuild().asString());
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    application.invokeLater(() -> application.exit(true, true, true, command));
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

  private static class ButtonAction extends AbstractAction {
    private final ButtonInfo myInfo;

    private ButtonAction(@NotNull ButtonInfo info) {
      super(info.getName());
      myInfo = info;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myInfo.isDownload()) {
        IdeUpdateUsageTriggerCollector.trigger( "dialog.download.clicked");
      }
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(myInfo.getUrl()));
    }
  }

  private class UpdateInfoPanel {
    private JPanel myPanel;
    private JEditorPane myUpdateMessage;
    private JBLabel myCurrentVersion;
    private JBLabel myNewVersion;
    private JBLabel myPatchLabel;
    private JBLabel myPatchInfo;
    private JEditorPane myMessageArea;
    private JEditorPane myLicenseArea;
    private JBScrollPane myScrollPane;

    UpdateInfoPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      ApplicationNamesInfo appNames = ApplicationNamesInfo.getInstance();

      String message = myNewBuild.getMessage();
      if (StringUtil.isEmptyOrSpaces(message)) {
        String url = downloadUrl();
        message = IdeBundle.message("updates.new.version.available", appNames.getFullProductName(), url);
      }
      configureMessageArea(myUpdateMessage, message, null, BrowserHyperlinkListener.INSTANCE);

      myCurrentVersion.setText(formatVersion(appInfo.getFullVersion(), appInfo.getBuild()));
      myNewVersion.setText(formatVersion(myNewBuild.getVersion(), myNewBuild.getNumber()));

      if (myPatches != null && !StringUtil.isEmptyOrSpaces(myPatches.getSize())) {
        myPatchInfo.setText(myPatches.getSize() + " MB");
      }
      else if (myTestPatch != null) {
        myPatchInfo.setText(Math.max(1, myTestPatch.length() >> 20) + " MB");
      }
      else {
        myPatchLabel.setVisible(false);
        myPatchInfo.setVisible(false);
      }

      if (myWriteProtected) {
        message = IdeBundle.message("updates.write.protected", appNames.getProductName(), PathManager.getHomePath());
        configureMessageArea(myMessageArea, message, JBColor.RED, null);
      }
      else {
        configureMessageArea(myMessageArea);
      }

      if (myLicenseInfo != null) {
        configureMessageArea(myLicenseArea, myLicenseInfo.first, myLicenseInfo.second, null);
      }
    }

    private void createUIComponents() {
      myUpdateMessage = new JEditorPane("text/html", "") {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
          Dimension size = super.getPreferredScrollableViewportSize();
          size.height = Math.min(size.height, JBUIScale.scale(400));
          return size;
        }
      };
      myScrollPane = new JBScrollPane(myUpdateMessage, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
      myScrollPane.setBorder(JBUI.Borders.empty());
    }
  }

  private static String formatVersion(String versionString, BuildNumber build) {
    return IdeBundle.message("updates.version.info", versionString, build.asStringWithoutProductCode());
  }
}