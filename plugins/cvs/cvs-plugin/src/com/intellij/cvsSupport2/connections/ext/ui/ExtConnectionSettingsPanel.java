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
package com.intellij.cvsSupport2.connections.ext.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;

/**
 * author: lesya
 */
public class ExtConnectionSettingsPanel {
  private TextFieldWithBrowseButton myPathToPrivateKeyFile;
  private TextFieldWithBrowseButton myPathToRsh;
  private JPanel myPanel;
  private JTextField myAdditionalParameters;
  private JLabel myRshLabel;
  private JLabel myAdditionalParametersLabel;
  private JLabel myPathToPPKLabel;

  public ExtConnectionSettingsPanel(Project project) {
    CvsConfigurationPanel.addBrowseHandler(project, myPathToRsh, CvsBundle.message("dialog.title.select.path.to.external.rsh"));
    CvsConfigurationPanel.addBrowseHandler(project, myPathToPrivateKeyFile, CvsBundle.message("dialog.title.select.path.to.ssh.private.key"));
    myRshLabel.setLabelFor(myPathToRsh.getTextField());
    myAdditionalParametersLabel.setLabelFor(myAdditionalParameters);
    myPathToPPKLabel.setLabelFor(myPathToPrivateKeyFile.getTextField());
  }

  public void updateFrom(ExtConfiguration ext_configuration) {
    myPathToPrivateKeyFile.setText(ext_configuration.PRIVATE_KEY_FILE);
    myPathToPrivateKeyFile.getTextField().selectAll();
    myPathToRsh.setText(ext_configuration.CVS_RSH);
    myPathToRsh.getTextField().selectAll();
    myAdditionalParameters.setText(ext_configuration.ADDITIONAL_PARAMETERS);
    myAdditionalParameters.selectAll();
  }

  public void saveTo(ExtConfiguration ext_configuration) {
    ext_configuration.CVS_RSH = myPathToRsh.getText();
    ext_configuration.PRIVATE_KEY_FILE = myPathToPrivateKeyFile.getText();
    ext_configuration.ADDITIONAL_PARAMETERS = myAdditionalParameters.getText();
  }

  public boolean equalsTo(ExtConfiguration ext_configuration) {
    if (!Comparing.equal(ext_configuration.CVS_RSH, myPathToRsh.getText())) {
      return false;
    }
    if (!Comparing.equal(ext_configuration.PRIVATE_KEY_FILE, myPathToPrivateKeyFile.getText())) {
      return false;
    }
    if (!Comparing.equal(ext_configuration.ADDITIONAL_PARAMETERS, myAdditionalParameters.getText())) {
      return false;
    }

    return true;
  }

  public JComponent getPanel() {
    return myPanel;
  }

}
