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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.pserver.ui.PServerSettingsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.CharsetToolkit;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;

/**
 * author: lesya
 */
public class GlobalCvsSettingsPanel {
  private final PServerSettingsPanel myPServerSettingsPanel;
  private JComponent myPanel;
  private JPanel myPServerPanel;
  private JCheckBox myUseGZIPCompression;
  private JComboBox myCharset;
  private JCheckBox myLogOutput;
  private JCheckBox mySendEnvironment;

  public GlobalCvsSettingsPanel(Project project) {
    myPServerPanel.setLayout(new BorderLayout());
    myPServerSettingsPanel = new PServerSettingsPanel(project);
    myPServerPanel.add(myPServerSettingsPanel.getPanel(), BorderLayout.CENTER);

    myCharset.addItem(CvsApplicationLevelConfiguration.DEFAULT);
    final Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();
    for (Charset charset : availableCharsets) {
      myCharset.addItem(charset.name());
    }
  }

  public void updateFrom(CvsApplicationLevelConfiguration config) {
    myPServerSettingsPanel.updateFrom(config);
    myCharset.setSelectedItem(config.ENCODING);
    myUseGZIPCompression.setSelected(config.USE_GZIP);
    myLogOutput.setSelected(config.DO_OUTPUT);
    mySendEnvironment.setSelected(config.SEND_ENVIRONMENT_VARIABLES_TO_SERVER);
  }

  public void saveTo(CvsApplicationLevelConfiguration config) {
    myPServerSettingsPanel.saveTo(config);
    final String oldEncoding = config.ENCODING;
    config.ENCODING = myCharset.getSelectedItem().toString();
    if (!Comparing.equal(oldEncoding, config.ENCODING)) {
      CvsEntriesManager.getInstance().encodingChanged();
    }
    config.USE_GZIP = myUseGZIPCompression.isSelected();
    config.DO_OUTPUT = myLogOutput.isSelected();
    config.SEND_ENVIRONMENT_VARIABLES_TO_SERVER = mySendEnvironment.isSelected();
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myCharset;
  }
}
