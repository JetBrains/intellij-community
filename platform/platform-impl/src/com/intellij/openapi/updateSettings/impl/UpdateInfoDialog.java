/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
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
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    init();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = new ArrayList<Action>();
    actions.add(getOKAction());

    final List<ButtonInfo> buttons = myLatestBuild.getButtons();

    if (hasPatch()) {
      if (buttons.isEmpty()) {
        actions.add(new AbstractAction(IdeBundle.message("updates.more.info.button")) {
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
    }
    else {
      // the first button replaces the OK action
      for (int i = 1; i < buttons.size(); i++) {
        actions.add(new ButtonAction(buttons.get(i)));
      }
    }
    actions.add(getCancelAction());
    actions.add(new AbstractAction("&Ignore This Update") {
          @Override
          public void actionPerformed(ActionEvent e) {
            UpdateSettings.getInstance().getIgnoredBuildNumbers().add(myLatestBuild.getNumber().asStringWithoutProductCode());
            doCancelAction();
          }
        });

    return actions.toArray(new Action[buttons.size()]);
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
    else if (myLatestBuild.getButtons().size() > 0) {
      return myLatestBuild.getButtons().get(0).getName();
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

    if (myLatestBuild.getButtons().size() > 0) {
      BrowserUtil.launchBrowser(myLatestBuild.getButtons().get(0).getUrl());
    }
    else {
      openDownloadPage();
    }
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
  
  private static class ButtonAction extends AbstractAction {
    private ButtonInfo myInfo;

    private ButtonAction(ButtonInfo info) {
      super(info.getName());
      myInfo = info;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      BrowserUtil.launchBrowser(myInfo.getUrl());
    }
  }

  private class UpdateInfoPanel {
    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JLabel myPatchAvailableLabel;
    private JLabel myPatchSizeLabel;
    private JEditorPane myUpdateMessageLabel;
    private JBScrollPane myScrollPane;
    private JLabel myManualCheckLabel;

    public UpdateInfoPanel() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      myBuildNumber.setText(appInfo.getBuild().asStringWithoutProductCode() + ")");
      final String version = appInfo.getFullVersion();

      myVersionNumber.setText(version);
      myNewBuildNumber.setText(myLatestBuild.getNumber().asStringWithoutProductCode() + ")");
      myNewVersionNumber.setText(myLatestBuild.getVersion());
      myUpdateMessageLabel.setBackground(UIUtil.getLabelBackground());
      myScrollPane.setBackground(UIUtil.getLabelBackground());
      myScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
      if (myLatestBuild.getMessage() != null) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())).append("</head><body>")
          .append(StringUtil.formatLinks(myLatestBuild.getMessage()))
          .append("</body></html>");
        myUpdateMessageLabel.setText(builder.toString());
        myUpdateMessageLabel.addHyperlinkListener(new BrowserHyperlinkListener());
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

      if (SystemInfo.isMac) {
        myManualCheckLabel.setText("<html><br>To check for new updates manually, use the <b>" +
                                   ApplicationNamesInfo.getInstance().getProductName() + " | Check for Updates</b> command.</html>");
      }

      LabelTextReplacingUtil.replaceText(myPanel);
    }
  }
}
