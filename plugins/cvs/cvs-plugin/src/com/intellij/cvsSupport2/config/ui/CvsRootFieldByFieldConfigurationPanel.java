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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsMethod;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.cvsSupport2.ui.FormUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class CvsRootFieldByFieldConfigurationPanel {

  private JComboBox myMethods;
  private JTextField myUser;
  private JTextField myHost;
  private JTextField myPort;
  private JTextField myRepository;
  private JPanel myPanel;

  public CvsRootFieldByFieldConfigurationPanel() {}

  public void updateFrom(CvsRootData config) {
    myMethods.removeAllItems();
    for (CvsMethod method : CvsMethod.AVAILABLE_METHODS) {
      myMethods.addItem(method);
    }

    myMethods.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final CvsMethod cvsMethod = (CvsMethod)myMethods.getSelectedItem();
        myUser.setEnabled(cvsMethod.hasUserValue());
        myHost.setEnabled(cvsMethod.hasHostValue());
        myPort.setEnabled(cvsMethod.hasPortValue());
      }
    });

    final CvsMethod method = config.METHOD;
    myMethods.setSelectedItem(method);
    myUser.setText(config.USER);
    myHost.setText(config.HOST);
    if (config.PORT > 0) {
      myPort.setText(String.valueOf(config.PORT));
    }
    myRepository.setText(config.REPOSITORY);
  }

  public String getSettings() {
    final int port = FormUtils.getPositiveIntFieldValue(myPort, true, true, 0xFFFF);
    final CvsMethod cvsMethod = (CvsMethod)myMethods.getSelectedItem();
    final String user = FormUtils.getFieldValue(myUser, cvsMethod.hasUserValue());
    final String host = FormUtils.getFieldValue(myHost, cvsMethod.hasHostValue());
    final String repository = FormUtils.getFieldValue(myRepository, true);
    return CvsRootConfiguration.createStringRepresentationOn(cvsMethod, user, host, port, repository);
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myMethods;
  }
}
