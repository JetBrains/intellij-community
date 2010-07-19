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
import com.intellij.util.net.AuthenticationPanel;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgVcsMessages;

import javax.swing.*;

public class HgUsernamePasswordDialog extends DialogWrapper {
  private AuthenticationPanel authPanel;

  public HgUsernamePasswordDialog(Project project, String login, String password) {
    super(project, false);
    setTitle(HgVcsMessages.message("hgidea.dialog.login.password.required"));
    authPanel = new AuthenticationPanel(null, login, password, !StringUtils.isBlank(password));
    init();
  }

  protected JComponent createCenterPanel() {
    return authPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return authPanel.getPreferredFocusedComponent();
  }

  public String getUsername() {
    return authPanel.getLogin();
  }

  public String getPassword() {
    return authPanel.getPassword();
  }

  public boolean isRememberPassword() {
    return authPanel.isRememberPassword();
  }
  
}