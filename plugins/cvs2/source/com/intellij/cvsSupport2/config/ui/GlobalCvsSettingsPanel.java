package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.pserver.ui.PServerSettingsPanel;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;

/**
 * author: lesya
 */
public class GlobalCvsSettingsPanel {
  private PServerSettingsPanel myPServerSettingsPanel = new PServerSettingsPanel();
  private JComponent myPanel;
  private JPanel myPServerPanel;
  private JCheckBox myUseGZIPCompression;
  private JComboBox myCharset;
  private JCheckBox myLogOutput;
  private JCheckBox mySendEnvironment;

  public GlobalCvsSettingsPanel() {
    myPServerPanel.setLayout(new BorderLayout());
    myPServerPanel.add(myPServerSettingsPanel.getPanel(), BorderLayout.CENTER);

    Charset[] availableCharsets = CharsetToolkit.getAvailableCharsets();

    myCharset.addItem(CvsApplicationLevelConfiguration.DEFAULT);
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
    String oldEncoding = config.ENCODING;
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
}
