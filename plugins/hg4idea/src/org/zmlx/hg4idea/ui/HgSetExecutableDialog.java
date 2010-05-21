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
import org.zmlx.hg4idea.HgVcsMessages;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HgSetExecutableDialog extends DialogWrapper {
  private JPanel myCenterPanel;
  private HgSetExecutablePathPanel myHgExecutablePath;
  private JLabel myInfoLabel;

  public HgSetExecutableDialog(Project project) {
    super(project, false);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  public void setBadHgPath(String hgPath) {
    myHgExecutablePath.setText(hgPath);
    setErrorText(HgVcsMessages.message("hg4idea.configuration.executable.error", hgPath));
  }

  public String getNewHgPath() {
    return myHgExecutablePath.getText();
  }

  private void createUIComponents() {
    myHgExecutablePath = new HgSetExecutablePathPanel();
    myHgExecutablePath.addOKListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        setErrorText("");
      }
    });
  }
}
