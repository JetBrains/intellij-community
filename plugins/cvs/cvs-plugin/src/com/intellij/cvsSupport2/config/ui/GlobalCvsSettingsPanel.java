// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.pserver.ui.PServerSettingsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import java.awt.BorderLayout;
import java.nio.charset.Charset;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

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
    if (!Objects.equals(oldEncoding, config.ENCODING)) {
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
