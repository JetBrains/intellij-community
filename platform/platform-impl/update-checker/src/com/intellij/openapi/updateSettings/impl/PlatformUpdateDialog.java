// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.CommandLineUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.Restarter;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.updateSettings.impl.UpdateCheckerService.SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY;
import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public final class PlatformUpdateDialog extends AbstractUpdateDialog {

  static final int LEFT_RIGHT_GAP = 20;
  /**
   * Default vertical component IntelliJSpacingConfiguration#getVerticalComponentGap = 6, which includes focus ring inset 3.
   * So without Kotlin UI DSL 6 - 3 = 3 should be additionally added
   */
  private static final int V_COMPONENT_GAP = 3;

  private final @Nullable Project myProject;
  private final PlatformUpdates.Loaded myPlatformUpdate;
  private final @Nullable Collection<PluginDownloader> myUpdatesForPlugins;
  private final boolean myWriteProtected;
  private final @Nullable LicenseInfo myLicenseInfo;
  private final @Nullable @Unmodifiable List<String> myIncompatiblePluginNames;
  private final @Nullable Path myTestPatch;

  private record LicenseInfo(@NlsContexts.Label String licenseNote, boolean warning) { }

  public PlatformUpdateDialog(
    @Nullable Project project,
    @NotNull PlatformUpdates.Loaded platformUpdate,
    boolean addConfigureUpdatesLink,
    @Nullable Collection<PluginDownloader> updatesForPlugins,
    @Nullable List<String> incompatiblePluginNames
  ) {
    this(project, platformUpdate, isPatchWriteProtected(platformUpdate), getLicensingInfo(platformUpdate), addConfigureUpdatesLink,
         updatesForPlugins, incompatiblePluginNames, false, null);
  }

  private PlatformUpdateDialog(
    @Nullable Project project,
    @NotNull PlatformUpdates.Loaded platformUpdate,
    boolean writeProtected,
    @Nullable LicenseInfo licenseInfo,
    boolean addConfigureUpdatesLink,
    @Nullable Collection<PluginDownloader> updatesForPlugins,
    @Nullable List<String> incompatiblePluginNames,
    boolean test,
    @Nullable Path patchFile
  ) {
    super(project, addConfigureUpdatesLink);
    myProject = project;
    myPlatformUpdate = platformUpdate;
    myUpdatesForPlugins = updatesForPlugins;
    myWriteProtected = writeProtected;
    myLicenseInfo = licenseInfo;
    myIncompatiblePluginNames = incompatiblePluginNames;
    myTestPatch = patchFile;
    init();
    if (test) {
      setTitle("[TEST] " + getTitle()); //NON-NLS
    }
    if (!test) {
      var patches = myPlatformUpdate.getPatches();
      IdeUpdateUsageTriggerCollector.triggerUpdateDialog(patches, ApplicationManager.getApplication().isRestartCapable());
    }
  }

  public static PlatformUpdateDialog createTestDialog(
    @Nullable Project project,
    @NotNull PlatformUpdates.Loaded platformUpdate,
    boolean writeProtected,
    @Nullable @NlsContexts.Label String licenseNote,
    boolean licenseNoteWarning,
    boolean addConfigureUpdatesLink,
    @Nullable Collection<PluginDownloader> updatesForPlugins,
    @Nullable List<String> incompatiblePluginNames
  ) {
    return new PlatformUpdateDialog(project, platformUpdate, writeProtected, getTestLicenseInfo(licenseNote, licenseNoteWarning),
                                    addConfigureUpdatesLink, updatesForPlugins, incompatiblePluginNames, true, null);
  }

  private static @Nullable LicenseInfo getTestLicenseInfo(
    @Nullable @NlsContexts.Label String licenseNote,
    boolean licenseNoteWarning) {
    return licenseNote == null ? null : new LicenseInfo(licenseNote, licenseNoteWarning);
  }

  public static PlatformUpdateDialog createTestDialog(
    @Nullable Project project,
    @NotNull PlatformUpdates.Loaded platformUpdate,
    @Nullable Path patchFile
  ) {
    return new PlatformUpdateDialog(project, platformUpdate, false, getLicensingInfo(platformUpdate), true, null, null, true, patchFile);
  }

  private static @Nullable LicenseInfo getLicensingInfo(PlatformUpdates.Loaded platformUpdate) {
    var la = LicensingFacade.getInstance();
    if (la == null) return null;

    var channel = platformUpdate.getUpdatedChannel();
    if (channel.getLicensing() == UpdateChannel.Licensing.EAP) {
      return new LicenseInfo(IdeBundle.message("updates.channel.bundled.key"), Boolean.FALSE);
    }

    var releaseDate = platformUpdate.getNewBuild().getReleaseDate();
    if (releaseDate == null) return null;

    if (!la.isApplicableForProduct(releaseDate)) {
      return new LicenseInfo(IdeBundle.message("updates.paid.upgrade", channel.getEvalDays()), Boolean.TRUE);
    }
    if (la.isPerpetualForProduct(releaseDate)) {
      return new LicenseInfo(IdeBundle.message("updates.fallback.build"), Boolean.FALSE);
    }

    var expiration = la.getLicenseExpirationDate();
    if (expiration != null) {
      return new LicenseInfo(IdeBundle.message("updates.interim.build", NlsMessages.formatDateLong(expiration)), Boolean.FALSE);
    }

    return null;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return UpdateInfoPanelKt.createUpdateInfoPanel(
      myPlatformUpdate.getNewBuild(),
      myPlatformUpdate.getPatches(),
      myTestPatch,
      myWriteProtected,
      myLicenseInfo != null ? myLicenseInfo.licenseNote : null,
      myLicenseInfo != null && myLicenseInfo.warning,
      myIncompatiblePluginNames,
      myAddConfigureUpdatesLink,
      myPlatformUpdate.getUpdatedChannel());
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Override
  protected @NotNull JComponent createSouthPanel() {
    var component = super.createSouthPanel();
    component.setBorder(JBUI.Borders.empty(V_COMPONENT_GAP, LEFT_RIGHT_GAP, 8 + V_COMPONENT_GAP, LEFT_RIGHT_GAP));
    return component;
  }

  @Override
  protected Action @NotNull [] createActions() {
    var actions = new ArrayList<Action>();
    actions.add(new AbstractAction(IdeBundle.message("updates.skip.update.button")) {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                    var build = myPlatformUpdate.getNewBuild().getNumber().asStringWithoutProductCode();
                    UpdateSettings.getInstance().getIgnoredBuildNumbers().add(build);
                    doCancelAction();
                  }
                }
    );
    actions.add(getCancelAction());

    var updateButton = (AbstractAction)null;
    if (myPlatformUpdate.getPatches() != null || myTestPatch != null) {
      var canRestart = ApplicationManager.getApplication().isRestartCapable();
      var name = IdeBundle.message(canRestart ? "updates.download.and.restart.button" : "updates.apply.manually.button");
      updateButton = new AbstractAction(name) {
        @Override
        public void actionPerformed(ActionEvent e) {
          close(OK_EXIT_CODE);
          var pluginIdsToUpdate = ContainerUtil.map2Set((myUpdatesForPlugins != null ? myUpdatesForPlugins : List.of()), PluginDownloader::getId);
          PluginModelAsyncOperationsExecutor.INSTANCE.findPlugins(pluginIdsToUpdate, plugins -> {
            downloadPatchAndRestart(plugins);
            return Unit.INSTANCE;
          });
        }
      };
      updateButton.setEnabled(!myWriteProtected);
    }
    else {
      var downloadUrl = myPlatformUpdate.getNewBuild().getDownloadUrl();
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

    return actions.toArray(new Action[0]);
  }

  @Override
  protected @NotNull String getCancelButtonText() {
    return IdeBundle.message("updates.remind.later.button");
  }

  static boolean isPatchWriteProtected(@NotNull PlatformUpdates.Loaded platformUpdate) {
    return platformUpdate.getPatches() != null && OS.CURRENT != OS.Windows && !Files.isWritable(PathManager.getHomeDir());
  }

  private void downloadPatchAndRestart(Map<PluginId, PluginUiModel> installedPlugins) {
    Collection<PluginDownloader> selectedPluginsToUpdate = new ArrayList<>();
    if (myUpdatesForPlugins != null && !installedPlugins.isEmpty()) {
      var dialog = new PluginUpdateDialog(myProject, new ArrayList<>(ContainerUtil.map(myUpdatesForPlugins, PluginDownloader::getUiModel)), null, installedPlugins);
      if (!dialog.showAndGet()) {
        return;  // update cancelled
      }
      Set<PluginId> selectedPlugins = ContainerUtil.map2Set(dialog.getSelectedPluginModels(), PluginUiModel::getPluginId);
      selectedPluginsToUpdate.addAll(ContainerUtil.filter(myUpdatesForPlugins, it -> selectedPlugins.contains(it.getId())));
    }

    startPatchTask(myProject, myPlatformUpdate, myTestPatch, selectedPluginsToUpdate);
  }

  static void startPatchTask(
    @Nullable Project project,
    @NotNull PlatformUpdates.Loaded platformUpdate,
    @Nullable Path testPatch,
    @NotNull Collection<PluginDownloader> pluginsToUpdate
  ) {
    IdeUpdateWidgetState.getInstance().updateStatus(IdeUpdateWidgetState.Status.DOWNLOADING);

    //noinspection UsagesOfObsoleteApi
    new Task.Backgroundable(project, IdeBundle.message("update.preparing"), true, PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        @NotNull String @NotNull [] command;
        try {
          if (testPatch != null) {
            command = UpdateInstaller.preparePatchCommand(List.of(testPatch), indicator);
          }
          else {
            var files = UpdateInstaller.downloadPatchChain(requireNonNull(platformUpdate.getPatches()).getChain(), indicator);
            command = UpdateInstaller.preparePatchCommand(files, indicator);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(PlatformUpdateDialog.class).warn(e);

          var title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
          var downloadUrl = UpdateInfoPanelKt.downloadUrl(platformUpdate.getNewBuild(), platformUpdate.getUpdatedChannel());
          var message = IdeBundle.message("update.downloading.patch.error", e.getMessage());
          UpdateChecker.getNotificationGroupForIdeUpdateResults()
            .createNotification(title, message, NotificationType.ERROR)
            .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("update.downloading.patch.open"), () -> BrowserUtil.browse(downloadUrl)))
            .setDisplayId("ide.patch.download.failed")
            .notify(project);

          return;
        }

        if (!ContainerUtil.isEmpty(pluginsToUpdate)) {
          UpdateInstaller.installPluginUpdates(pluginsToUpdate, indicator);
        }

        if (ApplicationManager.getApplication().isRestartCapable()) {
          if (IdeUpdateWidgetState.isWidgetShown()) {
            IdeUpdateWidgetState.getInstance().onRestartReady(command);
          }
          else if (indicator.isShowing()) {
            restartLaterAndRunCommand(command);
          }
          else {
            var title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
            var message = IdeBundle.message("update.ready.message");
            UpdateChecker.getNotificationGroupForIdeUpdateResults()
              .createNotification(title, message, NotificationType.INFORMATION)
              .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("update.ready.restart"), () -> restartLaterAndRunCommand(command)))
              .setDisplayId("ide.update.suggest.restart")
              .notify(project);
          }
        }
        else {
          showPatchInstructions(command);
        }
      }

      @Override
      public void onFinished() {
        IdeUpdateWidgetState.getInstance().onDownloadFinished();
      }
    }.queue();
  }

  static void restartLaterAndRunCommand(String[] command) {
    IdeUpdateUsageTriggerCollector.UPDATE_STARTED.log();
    PropertiesComponent.getInstance().setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, ApplicationInfo.getInstance().getBuild().asString());
    Restarter.setRestarterEnv(Map.of(ConfigImportHelper.IMPORT_FROM_ENV_VAR, PathManager.getConfigDir().toString()));
    var app = ApplicationManagerEx.getApplicationEx();
    app.invokeLater(() -> app.restart(ApplicationEx.EXIT_CONFIRMED | ApplicationEx.SAVE, command));
  }

  private static void showPatchInstructions(String[] command) {
    var product = ApplicationNamesInfo.getInstance().getFullProductName().replace(' ', '-').toLowerCase(Locale.ENGLISH);
    var version = ApplicationInfo.getInstance().getFullVersion();
    var file = Path.of(SystemProperties.getUserHome(), product + "-" + version + "-patch." + (OS.CURRENT == OS.Windows ? "cmd" : "sh"));
    try {
      var cmdLine = String.join(" ", CommandLineUtil.toCommandLine(Arrays.asList(command)));
      var text = (OS.CURRENT == OS.Windows ? "@echo off\n\n" : "#!/bin/sh\n\n") + cmdLine;
      Files.writeString(file, text);
      NioFiles.setExecutable(file);
    }
    catch (Exception e) {
      Logger.getInstance(PlatformUpdateDialog.class).error(e);
      return;
    }

    var title = IdeBundle.message("updates.dialog.title", ApplicationNamesInfo.getInstance().getFullProductName());
    var message = IdeBundle.message("update.apply.manually.message", file);
    IdeUpdateUsageTriggerCollector.MANUAL_PATCH_PREPARED.log();
    ApplicationManager.getApplication().invokeLater(() -> Messages.showInfoMessage(message, title));
  }
}
