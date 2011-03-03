/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author pti
 */
class UpdateInfoDialog extends AbstractUpdateDialog {
  private final UpdateChannel myUpdatedChannel;
  private final BuildInfo myLatestBuild;

  protected UpdateInfoDialog(final boolean canBeParent,
                             UpdateChannel channel,
                             final List<PluginDownloader> uploadedPlugins,
                             final boolean enableLink) {
    super(canBeParent, enableLink, uploadedPlugins);
    myUpdatedChannel = channel;
    myLatestBuild = channel.getLatestBuild();
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  @Override
  protected Action[] createActions() {
    AbstractAction ignore = new AbstractAction("&Ignore This Update") {
      @Override
      public void actionPerformed(ActionEvent e) {
        UpdateSettings.getInstance().getIgnoredBuildNumbers().add(myLatestBuild.getNumber().asStringWithoutProductCode());
        doCancelAction();
      }
    };
    if (hasPatch()) {
      AbstractAction moreInfo = new AbstractAction(IdeBundle.message("updates.more.info.button")) {
        public void actionPerformed(ActionEvent e) {
          openDownloadPage();
        }
      };
      return new Action[]{getOKAction(), moreInfo, getCancelAction(), ignore };
    }

    return new Action[] { getOKAction(), getCancelAction(), ignore };
  }

  private void openDownloadPage() {
    BrowserUtil.launchBrowser(myUpdatedChannel.getHomePageUrl());
  }

  protected String getOkButtonText() {
    if (hasPatch()) {
      return ApplicationManager.getApplication().isRestartCapable()
             ? IdeBundle.message("updates.download.and.install.patch.button.restart")
             : IdeBundle.message("updates.download.and.install.patch.button");
    }
    else {
      return IdeBundle.message("updates.more.info.button");
    }
  }

  @Override
  protected String getCancelButtonText() {
    return "&Remind Me Later";
  }

  protected JComponent createCenterPanel() {
    UpdateInfoPanel updateInfoPanel = new UpdateInfoPanel();
    return updateInfoPanel.myPanel;
  }

  @Override
  protected void doOKAction() {
    if (hasPatch()) {
      super.doOKAction();
      return;
    }

    openDownloadPage();
    super.doOKAction();
  }

  @Override
  protected boolean doDownloadAndPrepare() {
    if (hasPatch()) {
      switch (UpdateChecker.downloadAndInstallPatch(myLatestBuild)) {
        case CANCELED:
          return false;
        case FAILED:
          openDownloadPage();
          return false;
        case SUCCESS:
          super.doDownloadAndPrepare();
          return true;
      }
    }
    return super.doDownloadAndPrepare();
  }

  private boolean hasPatch() {
    return myLatestBuild.findPatchForCurrentBuild() != null;
  }

  private class UpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JLabel myPatchAvailableLabel;
    private JLabel myPatchSizeLabel;
    private JLabel myUpdateMessageLabel;
    private JLabel myMessageLabel;

    public UpdateInfoPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      myBuildNumber.setText(appInfo.getBuild().asStringWithoutProductCode() + ")");
      final String majorVersion = appInfo.getMajorVersion();
      final String version;
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        final String minorVersion = appInfo.getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          version = majorVersion + "." + minorVersion;
        }
        else {
          version = majorVersion + ".0";
        }
      }
      else {
        version = appInfo.getVersionName();
      }

      myVersionNumber.setText(version);
      myNewBuildNumber.setText(myLatestBuild.getNumber().asStringWithoutProductCode() + ")");
      myNewVersionNumber.setText(myLatestBuild.getVersion());
      if (myLatestBuild.getMessage() != null) {
        myUpdateMessageLabel.setText("<html><body><br>" + myLatestBuild.getMessage() + "</body></html>");
      }
      else {
        myUpdateMessageLabel.setVisible(false);
      }

      PatchInfo patch = myLatestBuild.findPatchForCurrentBuild();
      if (patch == null) {
        myPatchAvailableLabel.setVisible(false);
        myPatchSizeLabel.setVisible(false);
      }
      else {
        myPatchSizeLabel.setText(patch.getSize() + "MB");
      }
      LabelTextReplacingUtil.replaceText(myPanel);
    }
  }
}
