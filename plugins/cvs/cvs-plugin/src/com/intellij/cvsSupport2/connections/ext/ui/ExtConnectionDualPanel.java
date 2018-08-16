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
package com.intellij.cvsSupport2.connections.ext.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshConnectionSettingsPanel;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class ExtConnectionDualPanel {

  private final ExtConnectionSettingsPanel myExtSettingsPanel;
  private final SshConnectionSettingsPanel mySshSettingsPanel;

  private final Collection<CvsRootChangeListener> myCvsRootChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JPanel myDualPanel = new JPanel(new CardLayout());
  private final JCheckBox myUseInternalImplementationCheckBox = new JCheckBox(CvsBundle.message("checkbox.text.use.internal.ssh.implementation"));
  @NonNls private static final String EXT = "EXT";
  @NonNls private static final String SSH = "SSH";

  public ExtConnectionDualPanel(Project project) {
    myExtSettingsPanel = new ExtConnectionSettingsPanel(project);
    mySshSettingsPanel = new SshConnectionSettingsPanel(project);

    myDualPanel.add(myExtSettingsPanel.getPanel(), EXT);
    myDualPanel.add(mySshSettingsPanel.getPanel(), SSH);

    myPanel.add(myUseInternalImplementationCheckBox, BorderLayout.NORTH);
    myPanel.add(myDualPanel, BorderLayout.CENTER);

    myUseInternalImplementationCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updatePage();
        notifyListeners();
      }
    });
  }

  public void addCvsRootChangeListener(CvsRootChangeListener l) {
    myCvsRootChangeListeners.add(l);
  }

  private void notifyListeners() {
    for (CvsRootChangeListener cvsRootChangeListener : myCvsRootChangeListeners) {
      cvsRootChangeListener.onCvsRootChanged();
    }
  }

  private void updatePage() {
    final CardLayout cardLayout = (CardLayout)myDualPanel.getLayout();
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

  public boolean isUseInternalSshImplementation() {
    return myUseInternalImplementationCheckBox.isSelected();
  }
}
