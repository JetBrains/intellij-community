/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ui.BrowserHyperlinkListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Objects;

/**
 * @author yole
 */
class NewChannelDialog extends AbstractUpdateDialog {
  private final UpdateChannel myChannel;
  private final BuildInfo myLatestBuild;

  public NewChannelDialog(@NotNull UpdateChannel channel) {
    super(false);
    myChannel = channel;
    myLatestBuild = channel.getLatestBuild();
    assert myLatestBuild != null;

    initLicensingInfo(myChannel, myLatestBuild);

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new NewChannelPanel().myPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{
      getOKAction(),

      new AbstractAction(IdeBundle.message("updates.remind.later.button")) {
        @Override
        public void actionPerformed(ActionEvent e) {
          UpdateSettings.getInstance().forgetChannelId(myChannel.getId());
          doCancelAction();
        }
      },

      getCancelAction()
    };
  }

  @Override
  protected String getOkButtonText() {
    return IdeBundle.message("updates.more.info.button");
  }

  @Override
  protected String getCancelButtonText() {
    return IdeBundle.message("updates.ignore.update.button");
  }

  @Override
  protected void doOKAction() {
    BrowserUtil.browse(Objects.requireNonNull(myChannel.getHomePageUrl(), "missing URL for channel " + myChannel.getId()));
    super.doOKAction();
  }

  private class NewChannelPanel {
    private JPanel myPanel;
    private JEditorPane myMessageArea;
    private JEditorPane myLicenseArea;

    private NewChannelPanel() {
      String message = IdeBundle.message("updates.channel.name.message", myChannel.getName(), myLatestBuild.getMessage());
      configureMessageArea(myMessageArea, message, null, BrowserHyperlinkListener.INSTANCE);

      if (myLicenseInfo != null) {
        configureMessageArea(myLicenseArea, myLicenseInfo, null, BrowserHyperlinkListener.INSTANCE);
      }
      else {
        myLicenseArea.setVisible(false);
      }
    }
  }
}