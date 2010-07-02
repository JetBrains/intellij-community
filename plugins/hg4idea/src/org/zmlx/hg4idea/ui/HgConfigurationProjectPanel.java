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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgVersionCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HgConfigurationProjectPanel {

  private final HgProjectSettings myProjectSettings;

  private JPanel myMainPanel;
  private JCheckBox myCheckIncomingCbx;
  private JCheckBox myCheckOutgoingCbx;
  private JRadioButton myAutoRadioButton;
  private JRadioButton mySelectRadioButton;
  private TextFieldWithBrowseButton myPathSelector;

  public HgConfigurationProjectPanel(HgProjectSettings projectSettings) {
    myProjectSettings = projectSettings;
    loadSettings();

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathSelector.setEnabled(mySelectRadioButton.isSelected());
      }
    };
    mySelectRadioButton.addActionListener(listener);
    myAutoRadioButton.addActionListener(listener);
  }

  public boolean isModified() {
    boolean executableModified = mySelectRadioButton.isSelected()
                                 ? !myPathSelector.getText().equals(myProjectSettings.getHgExecutable())
                                 : myAutoRadioButton.isSelected() != myProjectSettings.isAutodetectHg();
    return executableModified || myCheckIncomingCbx.isSelected() != myProjectSettings.isCheckIncoming()
           || myCheckOutgoingCbx.isSelected() != myProjectSettings.isCheckOutgoing();
  }

  public void saveSettings() {
    myProjectSettings.setCheckIncoming(myCheckIncomingCbx.isSelected());
    myProjectSettings.setCheckOutgoing(myCheckOutgoingCbx.isSelected());

    if (myAutoRadioButton.isSelected()) {
      myProjectSettings.enableAutodetectHg();
    } else {
      myProjectSettings.setHgExecutable(myPathSelector.getText());
    }
  }

  public void loadSettings() {
    myCheckIncomingCbx.setSelected(myProjectSettings.isCheckIncoming());
    myCheckOutgoingCbx.setSelected(myProjectSettings.isCheckOutgoing());

    boolean isAutodetectHg = myProjectSettings.isAutodetectHg();
    myAutoRadioButton.setSelected(isAutodetectHg);
    mySelectRadioButton.setSelected(!isAutodetectHg);
    myPathSelector.setEnabled(!isAutodetectHg);
    if (isAutodetectHg) {
      myPathSelector.setText(StringUtils.EMPTY);
    } else {
      myPathSelector.setText(myProjectSettings.getHgExecutable());
    }
  }

  public JPanel getPanel() {
    return myMainPanel;
  }

  public void validate() throws ConfigurationException {
    String hgExecutable;
    if (myAutoRadioButton.isSelected()) {
      hgExecutable = HgGlobalSettings.getDefaultExecutable();
    } else {
      hgExecutable = myPathSelector.getText();
    }
    HgVersionCommand command = new HgVersionCommand();
    if (!command.isValid(hgExecutable)) {
      throw new ConfigurationException(
        HgVcsMessages.message("hg4idea.configuration.executable.error", hgExecutable)
      );
    }
  }

  private void createUIComponents() {
    myPathSelector = new HgSetExecutablePathPanel();
  }

}
