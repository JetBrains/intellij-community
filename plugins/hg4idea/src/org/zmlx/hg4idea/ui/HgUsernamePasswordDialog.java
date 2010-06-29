// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class HgUsernamePasswordDialog extends DialogWrapper {
  private JPanel basePanel;
  private JTextField usernameTxt;
  private JPasswordField passwordTxt;

  public HgUsernamePasswordDialog(Project project) {
    super(project, false);
    setTitle("Username and password required");
    init();
  }

  public void setUsername(String username) {
    usernameTxt.setText(username);
  }

  public String getUsername() {
    return usernameTxt.getText();
  }

  public char[] getPassword() {
    return passwordTxt.getPassword();
  }

  protected JComponent createCenterPanel() {
    return basePanel;
  }

}
