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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.LicensingFacade;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class NewChannelDialog extends DialogWrapper {
  private final UpdateChannel myChannel;
  private String myInformationText;

  public NewChannelDialog(UpdateChannel channel) {
    super(true);
    myChannel = channel;
    setTitle("New " + ApplicationNamesInfo.getInstance().getFullProductName() + " Version Available");
    initInfo();
    init();
    setOKButtonText("More Info...");
    setOKButtonMnemonic('M');
    setCancelButtonText("Ignore This Update");
  }

  @Override
  protected Action[] createActions() {
    Action remindLater = new AbstractAction("Remind Me Later") {
      @Override
      public void actionPerformed(ActionEvent e) {
        UpdateSettings.getInstance().forgetChannelId(myChannel.getId());
        doCancelAction();
      }
    };
    return new Action[] { getOKAction(), remindLater, getCancelAction() };
  }

  @Override
  protected JComponent createCenterPanel() {
    return new JLabel(myInformationText);
  }

  private void initInfo() {
    StringBuilder builder = new StringBuilder().append("<html><b>").append(myChannel.getName()).append("</b><br>");
    LicensingFacade facade = LicensingFacade.getInstance();
    if (facade != null) {
      if (!myChannel.getLicensing().equals(UpdateChannel.LICENSING_EAP)) {
        Boolean paidUpgrade = facade.isPaidUpgrade(myChannel.getMajorVersion(), myChannel.getLatestBuild().getReleaseDate());
        if (paidUpgrade != null) {
          if (paidUpgrade) {
            builder.append("The new version requires upgrading your license key.");
          }
          else {
            builder.append("The new version can be used with your existing license key.");
          }
        }
      }
      else {
        builder.append("The new version has an expiration date and does not require a license key.");
      }
    }
    myInformationText = builder.toString();
  }

  @Override
  protected void doOKAction() {
    BrowserUtil.launchBrowser(myChannel.getHomePageUrl());
    super.doOKAction();
  }
}
