// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ext.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

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
    if (!Objects.equals(ext_configuration.CVS_RSH, myPathToRsh.getText())) {
      return false;
    }
    if (!Objects.equals(ext_configuration.PRIVATE_KEY_FILE, myPathToPrivateKeyFile.getText())) {
      return false;
    }
    if (!Objects.equals(ext_configuration.ADDITIONAL_PARAMETERS, myAdditionalParameters.getText())) {
      return false;
    }

    return true;
  }

  public JComponent getPanel() {
    return myPanel;
  }

}
