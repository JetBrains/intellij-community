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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HgConfigurationProjectPanel {

  @NotNull private final HgProjectSettings myProjectSettings;

  private JPanel myMainPanel;
  private JCheckBox myCheckIncomingOutgoingCbx;
  private TextFieldWithBrowseButton myPathSelector;
  private JButton myTestButton;
  private final HgVcs myVcs;

  public HgConfigurationProjectPanel(@NotNull HgProjectSettings projectSettings, @NotNull Project project) {
    myProjectSettings = projectSettings;
    myVcs = HgVcs.getInstance(project);
    loadSettings();
    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String executable = getCurrentPath();
        HgVersion version;
        try {
          version = HgVersion.identifyVersion(executable);
        }
        catch (Exception exception) {
          Messages.showErrorDialog(myMainPanel, exception.getMessage(), HgVcsMessages.message("hg4idea.run.failed.title"));
          return;
        }
        Messages.showInfoMessage(myMainPanel, String.format("Mercurial version is %s", version.toString()),
                                 HgVcsMessages.message("hg4idea.run.success.title")
        );
      }
    });
  }

  public boolean isModified() {
    boolean executableModified = !getCurrentPath().equals(myProjectSettings.getHgExecutable());
    return executableModified || myCheckIncomingOutgoingCbx.isSelected() != myProjectSettings.isCheckIncomingOutgoing();
  }

  public void saveSettings() {
    myProjectSettings.setCheckIncomingOutgoing(myCheckIncomingOutgoingCbx.isSelected());
    myProjectSettings.setHgExecutable(getCurrentPath());
    myVcs.checkVersion();
  }

  private String getCurrentPath() {
    return myPathSelector.getText().trim();
  }

  public void loadSettings() {
    myCheckIncomingOutgoingCbx.setSelected(myProjectSettings.isCheckIncomingOutgoing() );
    myPathSelector.setText(myProjectSettings.getGlobalSettings().getHgExecutable());
  }

  public JPanel getPanel() {
    return myMainPanel;
  }

  public void validate() throws ConfigurationException {
    String hgExecutable;
    hgExecutable = getCurrentPath();
    if (!HgUtil.isExecutableValid(hgExecutable)) {
      throw new ConfigurationException(
        HgVcsMessages.message("hg4idea.configuration.executable.error", hgExecutable)
      );
    }
  }

  private void createUIComponents() {
    myPathSelector = new HgSetExecutablePathPanel(myProjectSettings);
  }

  @NotNull
  public HgProjectSettings getProjectSettings() {
    return myProjectSettings;
  }
}
