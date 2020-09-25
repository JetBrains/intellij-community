// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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
  private final @Nullable Pair<@NlsContexts.Label String, Boolean> myLicenseInfo;
  private final File myTestPatch;
  private final AbstractAction myWhatsNewAction;

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
        String title = "What's new in " + ApplicationInfo.getInstance().getFullVersion();
        String url = myNewBuild.getBlogPost() + WhatsNewAction.getEmbeddedSuffix();
        HTMLEditorProvider.Companion.openEditor(project, title, url, null, myNewBuild.getMessage());
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
    return UpdateInfoPanel
      .create(myNewBuild, myPatches, myTestPatch, myWriteProtected, licenseInfo, licenseWarn, myEnableLink, myUpdatedChannel);
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
    boolean updatePlugins = !ContainerUtil.isEmpty(myUpdatedPlugins);
    if (updatePlugins && !new PluginUpdateInfoDialog(myUpdatedPlugins).showAndGet()) {
      return;  // update cancelled
    }

    Task.Backgroundable task = new PatchDownloadTask(myUpdatedChannel, myUpdatedPlugins,
                                                     myNewBuild, myPatches, myTestPatch).invoke();
    if (task != null) {
      task.queue();
    }
  }
}
