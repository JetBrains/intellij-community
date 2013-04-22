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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class NewChannelDialog extends DialogWrapper {
  private final UpdateChannel myChannel;
  private String myInformationText;
  private boolean myShowUpgradeButton = false;

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

  @NotNull
  @Override
  protected Action[] createActions() {
    Action remindLater = new AbstractAction("Remind Me Later") {
      @Override
      public void actionPerformed(ActionEvent e) {
        UpdateSettings.getInstance().forgetChannelId(myChannel.getId());
        doCancelAction();
      }
    };
    if (myShowUpgradeButton) {
      Action upgrade = new AbstractAction("Buy Upgrade Online") {
        @Override
        public void actionPerformed(ActionEvent e) {
          LicensingFacade facade = LicensingFacade.getInstance();
          assert facade != null;
          BrowserUtil.launchBrowser(facade.getUpgradeUrl());
          doCancelAction();
        }
      };
      return new Action[] { getOKAction(), upgrade, remindLater, getCancelAction() };
    }
    return new Action[] { getOKAction(), remindLater, getCancelAction() };
  }

  @Override
  protected JComponent createCenterPanel() {
    JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, myInformationText);
    pane.addHyperlinkListener(new BrowserHyperlinkListener());
    pane.setEditable(false);
    pane.setBackground(UIUtil.getLabelBackground());
    JBScrollPane scrollPane = new JBScrollPane(pane);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    return scrollPane;
  }

  private void initInfo() {
    StringBuilder builder = new StringBuilder();
    builder.append("<head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())).append("</head><body>");
    builder.append("<b>").append(myChannel.getName()).append("</b><br>");
    builder.append(StringUtil.formatLinks(myChannel.getLatestBuild().getMessage())).append("<br><br>");
    LicensingFacade facade = LicensingFacade.getInstance();
    if (facade != null) {
      if (!myChannel.getLicensing().equals(UpdateChannel.LICENSING_EAP)) {
        Boolean paidUpgrade = facade.isPaidUpgrade(myChannel.getMajorVersion(), myChannel.getLatestBuild().getReleaseDate());
        if (paidUpgrade != null) {
          if (paidUpgrade) {
            builder.append("You can evaluate the new version for ")
              .append(myChannel.getEvalDays())
              .append(" days or buy a license key or an upgrade online.");
            myShowUpgradeButton = true;
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
    myInformationText = XmlStringUtil.wrapInHtml(builder);
  }

  @Override
  protected void doOKAction() {
    BrowserUtil.launchBrowser(myChannel.getHomePageUrl());
    super.doOKAction();
  }
}
