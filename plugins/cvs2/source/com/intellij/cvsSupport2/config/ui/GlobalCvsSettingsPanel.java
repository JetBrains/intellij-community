package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.pserver.ui.PServerSettingsPanel;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class GlobalCvsSettingsPanel {
  private PServerSettingsPanel myPServerSettingsPanel = new PServerSettingsPanel();
  private JComponent myPanel;
    private JCheckBox myUseUtf8;
  private JPanel myPServerPanel;
  private JCheckBox myUseGZIPCompression;

  public GlobalCvsSettingsPanel() {
    myPServerPanel.setLayout(new BorderLayout());
    myPServerPanel.add(myPServerSettingsPanel.getPanel(), BorderLayout.CENTER);

  }

  public void updateFrom(CvsApplicationLevelConfiguration config) {
    myPServerSettingsPanel.updateFrom(config);
    myUseUtf8.setSelected(config.USE_UTF8);
    myUseGZIPCompression.setSelected(config.USE_GZIP);
  }

  public void saveTo(CvsApplicationLevelConfiguration config) {
    myPServerSettingsPanel.saveTo(config);
    config.USE_UTF8 = myUseUtf8.isSelected();
    config.USE_GZIP = myUseGZIPCompression.isSelected();

  }

  public JComponent getPanel() {
    return myPanel;
  }
}
