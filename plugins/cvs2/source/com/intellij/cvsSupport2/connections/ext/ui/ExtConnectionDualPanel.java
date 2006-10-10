package com.intellij.cvsSupport2.connections.ext.ui;

import com.intellij.cvsSupport2.config.CvsRootEditor;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshConnectionSettingsPanel;
import com.intellij.CvsBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExtConnectionDualPanel {

  private final ExtConnectionSettingsPanel myExtSettingsPanel;
  private final SshConnectionSettingsPanel mySshSettingsPanel;

  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JPanel myDualPanel = new JPanel(new CardLayout());
  private JCheckBox myUseInternalImplementationCheckBox = new JCheckBox(CvsBundle.message("checkbox.text.use.internal.ssh.implementation"));
  @NonNls private static final String EXT = "EXT";
  @NonNls private static final String SSH = "SSH";

  public ExtConnectionDualPanel(final CvsRootEditor rootProvider) {
    myExtSettingsPanel = new ExtConnectionSettingsPanel();
    mySshSettingsPanel = new SshConnectionSettingsPanel(rootProvider);

    myDualPanel.add(myExtSettingsPanel.getPanel(), EXT);
    myDualPanel.add(mySshSettingsPanel.getPanel(), SSH);


    myPanel.add(myUseInternalImplementationCheckBox, BorderLayout.NORTH);
    myPanel.add(myDualPanel, BorderLayout.CENTER);

    myUseInternalImplementationCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updatePage();
      }
    });

  }

  private void updatePage() {
    CardLayout cardLayout = ((CardLayout)myDualPanel.getLayout());

    if (myUseInternalImplementationCheckBox.isSelected()){
      cardLayout.show(myDualPanel, SSH);
    } else {
      cardLayout.show(myDualPanel, EXT);
    }

  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void updateFrom(ExtConfiguration extConfiguration, SshSettings sshSettings) {
    myExtSettingsPanel.updateFrom(extConfiguration);
    mySshSettingsPanel.updateFrom(sshSettings);
    myUseInternalImplementationCheckBox.setSelected(extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION);
    updatePage();
  }

  public boolean equalsTo(ExtConfiguration extConfiguration, SshSettings sshSettings) {
    if (!myExtSettingsPanel.equalsTo(extConfiguration)) {
      return false;
    }
    if (!mySshSettingsPanel.equalsTo(sshSettings)) {
      return false;
    }
    return myUseInternalImplementationCheckBox.isSelected() == extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION;
  }

  public void saveTo(ExtConfiguration extConfiguration, SshSettings sshSettings) {
    myExtSettingsPanel.saveTo(extConfiguration);
    mySshSettingsPanel.saveTo(sshSettings);
    extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION = myUseInternalImplementationCheckBox.isSelected();
  }
}