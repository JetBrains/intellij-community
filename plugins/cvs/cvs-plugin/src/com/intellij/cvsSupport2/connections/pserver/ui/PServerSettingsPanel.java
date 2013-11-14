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
package com.intellij.cvsSupport2.connections.pserver.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * author: lesya
 */
public class PServerSettingsPanel {
  private TextFieldWithBrowseButton myPathToPasswordFile;
  private JTextField myTimeout;
  private JPanel myPanel;
  private JLabel myConnectionTimeoutLabel;
  private JLabel myPasswordFileLabel;

  public PServerSettingsPanel(Project project) {
    CvsConfigurationPanel.addBrowseHandler(project, myPathToPasswordFile,
                                           CvsBundle.message("dialog.title.select.path.to.cvs.password.file"));
    myConnectionTimeoutLabel.setLabelFor(myTimeout);
    myPasswordFileLabel.setLabelFor(myPathToPasswordFile.getTextField());
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void updateFrom(CvsApplicationLevelConfiguration config) {
    myPathToPasswordFile.setText(config.getPathToPassFilePresentation());
    myPathToPasswordFile.getTextField().selectAll();
    myTimeout.setText(String.valueOf(config.TIMEOUT));
    myTimeout.selectAll();
  }

  public void saveTo(CvsApplicationLevelConfiguration config) {
    final String pathToPasswordFile = CvsApplicationLevelConfiguration.convertToIOFilePath(myPathToPasswordFile.getText());
    final File passwordFile = new File(pathToPasswordFile);
    if (!passwordFile.exists()) {
      final int result = Messages.showYesNoDialog(myPanel, CvsBundle.message("message.password.file.does.not.exist", pathToPasswordFile),
                                             CvsBundle.message("title.password.file.does.not.exist"), Messages.getQuestionIcon());
      if (result == Messages.NO) {
        throw new InputException(myPathToPasswordFile);
      }
      try {
        passwordFile.createNewFile();
      }
      catch (IOException e) {
        throw new InputException(e.getMessage(), myPathToPasswordFile);
      }
    }
    config.setPathToPasswordFile(pathToPasswordFile);
    try {
      final int timeout = Integer.parseInt(myTimeout.getText());
      if (timeout < 0) {
        throwInvalidTimeoutException();
      }
      config.TIMEOUT = timeout;
    }
    catch (NumberFormatException ex) {
      throwInvalidTimeoutException();
    }
  }

  private void throwInvalidTimeoutException() {
    throw new InputException(CvsBundle.message("exception.message.invalid.timeout.value", myTimeout.getText()), myTimeout);
  }
}
