/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

/**
 * @author pti
 */
class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChannel myUpdatedChannel;
  private final BuildInfo myLatestBuild;
  private final PatchInfo myPatch;
  private final boolean myWriteProtected;

  protected UpdateInfoDialog(@NotNull UpdateChannel channel, boolean enableLink) {
    super(enableLink);
    myUpdatedChannel = channel;
    myLatestBuild = channel.getLatestBuild();
    myPatch = myLatestBuild != null ? myLatestBuild.findPatchForCurrentBuild() : null;
    myWriteProtected = myPatch != null && !new File(PathManager.getHomePath()).canWrite();
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    init();
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
          downloadPatch();
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
        if (!info.isDownload()) {
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

  private void downloadPatch() {
    UpdateChecker.DownloadPatchResult result = UpdateChecker.downloadAndInstallPatch(myLatestBuild);
    if (result == UpdateChecker.DownloadPatchResult.SUCCESS) {
      restart();
    }
    else if (result == UpdateChecker.DownloadPatchResult.FAILED) {
      openDownloadPage();
    }
  }

  private void openDownloadPage() {
    BrowserUtil.launchBrowser(myUpdatedChannel.getHomePageUrl());
  }

  private static class ButtonAction extends AbstractAction {
    private final String myUrl;

    private ButtonAction(ButtonInfo info) {
      super(info.getName());
      myUrl = info.getUrl();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      BrowserUtil.launchBrowser(myUrl);
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

    public UpdateInfoPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      ApplicationNamesInfo appNames = ApplicationNamesInfo.getInstance();

      String message = myLatestBuild.getMessage();
      if (message == null) {
        message = IdeBundle.message("updates.new.version.available", appNames.getFullProductName());
      }
      configureMessageArea(myUpdateMessage, message, null, new BrowserHyperlinkListener());

      myCurrentVersion.setText(formatVersion(appInfo.getFullVersion(), appInfo.getBuild().asStringWithoutProductCode()));
      myNewVersion.setText(formatVersion(myLatestBuild.getVersion(), myLatestBuild.getNumber().asStringWithoutProductCode()));

      if (myPatch != null) {
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
    }

    private String formatVersion(String version, String build) {
      String[] parts = version.split("\\.", 3);
      String major = parts.length > 0 ? parts[0] : "0";
      String minor = parts.length > 1 ? parts[1] : "0";
      String patch = parts.length > 2 ? parts[2] : "0";
      version = major + '.' + minor + '.' + patch;

      return IdeBundle.message("updates.version.info", version, build);
    }
  }
}
