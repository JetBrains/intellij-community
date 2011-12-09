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
package com.intellij.cvsSupport2.connections.ssh.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class SshConnectionSettingsPanel {
  private TextFieldWithBrowseButton myPathToPrivateKeyFile;
  private JCheckBox myUsePrivateKeyFile;
  private JPanel myPanel;

  public SshConnectionSettingsPanel(Project project) {
    CvsConfigurationPanel.addBrowseHandler(project, myPathToPrivateKeyFile, CvsBundle.message("dialog.title.path.to.private.key.file"));
    myUsePrivateKeyFile.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setPathToPPKEnabled();
      }
    });
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void updateFrom(SshSettings ssh_configuration) {
    myUsePrivateKeyFile.setSelected(ssh_configuration.USE_PPK);
    myPathToPrivateKeyFile.setText(ssh_configuration.PATH_TO_PPK);
    setPathToPPKEnabled();
  }

  private void setPathToPPKEnabled() {
    if (!myUsePrivateKeyFile.isSelected()) {
      myPathToPrivateKeyFile.setEnabled(false);
    }
    else {
      myPathToPrivateKeyFile.setEnabled(true);
      myPathToPrivateKeyFile.getTextField().selectAll();
      myPathToPrivateKeyFile.getTextField().requestFocus();
    }
  }

  public void saveTo(SshSettings ssh_configuration) {
    if (myUsePrivateKeyFile.isSelected() && myPathToPrivateKeyFile.getText().trim().length() == 0){
      throw new InputException(CvsBundle.message("error.message.path.to.private.key.file.must.not.be.empty"),
                               myPathToPrivateKeyFile.getTextField());
    }
    ssh_configuration.USE_PPK = myUsePrivateKeyFile.isSelected();
    ssh_configuration.PATH_TO_PPK = myPathToPrivateKeyFile.getText().trim();
  }

  public boolean equalsTo(SshSettings ssh_configuration) {
    if (ssh_configuration.USE_PPK != myUsePrivateKeyFile.isSelected()) {
      return false;
    }
    return ssh_configuration.PATH_TO_PPK.equals(myPathToPrivateKeyFile.getText().trim());
  }
}
