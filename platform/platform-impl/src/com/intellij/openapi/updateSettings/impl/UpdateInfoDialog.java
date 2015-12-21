/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author pti
 */
class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChannel myUpdatedChannel;
  private final boolean myForceHttps;
  private final Collection<PluginDownloader> myUpdatedPlugins;
  private final BuildInfo myLatestBuild;
  private final PatchInfo myPatch;
  private final boolean myWriteProtected;

  protected UpdateInfoDialog(@NotNull UpdateChannel channel,
                             @NotNull BuildInfo latestBuild,
                             boolean enableLink,
                             boolean forceHttps,
                             Collection<PluginDownloader> updatedPlugins,
                             Collection<IdeaPluginDescriptor> incompatiblePlugins) {
    super(enableLink);
    myUpdatedChannel = channel;
    myForceHttps = forceHttps;
    myUpdatedPlugins = updatedPlugins;
    myLatestBuild = latestBuild;
    myPatch = myLatestBuild.findPatchForCurrentBuild();
    myWriteProtected = myPatch != null && !new File(PathManager.getHomePath()).canWrite();
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    initLicensingInfo(myUpdatedChannel, myLatestBuild);
    init();

    if (incompatiblePlugins != null && !incompatiblePlugins.isEmpty()) {
      String list = StringUtil.join(incompatiblePlugins, new Function<IdeaPluginDescriptor, String>() {
        @Override
        public String fun(IdeaPluginDescriptor downloader) {
          return downloader.getName();
        }
      }, "<br/>");
      setErrorText(IdeBundle.message("updates.incompatible.plugins.found", incompatiblePlugins.size(), list));
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return new UpdateInfoPanel().myPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = ContainerUtil.newArrayList();

    if (myPatch != null) {
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
      String button = IdeBundle.message(canRestart ? "updates.download.and.restart.button" : "updates.download.and.install.button");
      actions.add(new AbstractAction(button) {
        {
          setEnabled(!myWriteProtected);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          downloadPatchAndRestart();
        }
      });
    }

    List<ButtonInfo> buttons = myLatestBuild.getButtons();
    if (buttons.isEmpty()) {
      actions.add(new AbstractAction(IdeBundle.message("updates.more.info.button")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          openDownloadPage();
        }
      });
    }
    else {
      for (ButtonInfo info : buttons) {
        if (!info.isDownload() || myPatch == null) {
          actions.add(new ButtonAction(info));
        }
      }
    }

    actions.add(new AbstractAction(IdeBundle.message("updates.ignore.update.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        String build = myLatestBuild.getNumber().asStringWithoutProductCode();
        UpdateSettings.getInstance().getIgnoredBuildNumbers().add(build);
        doCancelAction();
      }
    });

    actions.add(getCancelAction());

    return actions.toArray(new Action[actions.size()]);
  }

  @Override
  protected String getCancelButtonText() {
    return IdeBundle.message("updates.remind.later.button");
  }

  private void downloadPatchAndRestart() {
    try {
      UpdateChecker.installPlatformUpdate(myPatch, myLatestBuild.getNumber(), myForceHttps);

      if (myUpdatedPlugins != null && !myUpdatedPlugins.isEmpty()) {
        new PluginUpdateInfoDialog(getContentPanel(), myUpdatedPlugins).show();
      }

      restart();
    }
    catch (IOException e) {
      Logger.getInstance(UpdateChecker.class).warn(e);
      if (Messages.showOkCancelDialog(IdeBundle.message("update.downloading.patch.error", e.getMessage()),
                                      IdeBundle.message("updates.error.connection.title"),
                                      IdeBundle.message("updates.download.page.button"), CommonBundle.message("button.cancel"),
                                      Messages.getErrorIcon()) == Messages.OK) {
        openDownloadPage();
      }
    }
  }

  private void openDownloadPage() {
    String url = myUpdatedChannel.getHomePageUrl();
    assert url != null : "channel: " + myUpdatedChannel.getId();
    BrowserUtil.browse(url);
  }

  private static class ButtonAction extends AbstractAction {
    private final String myUrl;

    private ButtonAction(ButtonInfo info) {
      super(info.getName());
      myUrl = info.getUrl();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      BrowserUtil.browse(myUrl);
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

    public UpdateInfoPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      ApplicationNamesInfo appNames = ApplicationNamesInfo.getInstance();

      String message = myLatestBuild.getMessage();
      final String fullProductName = appNames.getFullProductName();
      if (StringUtil.isEmpty(message)) {
        message = IdeBundle.message("updates.new.version.available", fullProductName);
      }
      final String homePageUrl = myUpdatedChannel.getHomePageUrl();
      if (!StringUtil.isEmptyOrSpaces(homePageUrl)) {
        final int idx = message.indexOf(fullProductName);
        if (idx >= 0) {
          message = message.substring(0, idx) + 
                    "<a href=\'" + homePageUrl + "\'>" + fullProductName + "</a>" + message.substring(idx + fullProductName.length());
        }
      }
      configureMessageArea(myUpdateMessage, message, null, BrowserHyperlinkListener.INSTANCE);

      myCurrentVersion.setText(
        formatVersion(
          appInfo.getFullVersion() + (appInfo instanceof ApplicationInfoEx && ((ApplicationInfoEx)appInfo).isEAP() ? " EAP": ""),
          appInfo.getBuild().asStringWithoutProductCode()
        )
      );
      myNewVersion.setText(formatVersion(myLatestBuild.getVersion(), myLatestBuild.getNumber().asStringWithoutProductCode()));

      if (myPatch != null && !StringUtil.isEmptyOrSpaces(myPatch.getSize())) {
        myPatchInfo.setText(myPatch.getSize() + " MB");
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
        configureMessageArea(myLicenseArea, myLicenseInfo, myPaidUpgrade ? JBColor.RED : null, null);
      }
    }
  }

  protected static String formatVersion(String versionString, String build) {
    Version version = Version.parseVersion(versionString);
    String formattedVersion = version != null ? version.toString() : versionString;
    return IdeBundle.message("updates.version.info", formattedVersion, build);
  }
}