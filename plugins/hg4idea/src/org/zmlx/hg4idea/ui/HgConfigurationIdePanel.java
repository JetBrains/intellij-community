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
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgVersionCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HgConfigurationIdePanel {
  private JRadioButton autoRadioButton;
  private JRadioButton selectRadioButton;
  private TextFieldWithBrowseButton pathSelector;
  private JPanel basePanel;

  private final HgGlobalSettings globalSettings;

  public HgConfigurationIdePanel(HgGlobalSettings globalSettings) {
    this.globalSettings = globalSettings;
    loadSettings();

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        pathSelector.setEnabled(selectRadioButton.isSelected());
      }
    };

    selectRadioButton.addActionListener(listener);
    autoRadioButton.addActionListener(listener);
  }

  public boolean isModified() {
    if (selectRadioButton.isSelected()) {
      return !pathSelector.getText().equals(globalSettings.getHgExecutable());
    }
    return autoRadioButton.isSelected() != globalSettings.isAutodetectHg();
  }

  public JPanel getBasePanel() {
    return basePanel;
  }

  public void validate() throws ConfigurationException {
    String hgExecutable;
    if (autoRadioButton.isSelected()) {
      hgExecutable = HgGlobalSettings.getDefaultExecutable();
    } else {
      hgExecutable = pathSelector.getText();
    }
    HgVersionCommand command = new HgVersionCommand();
    if (!command.isValid(hgExecutable)) {
      throw new ConfigurationException(
        HgVcsMessages.message("hg4idea.configuration.executable.error", hgExecutable)
      );
    }
  }

  public void saveSettings() {
    if (autoRadioButton.isSelected()) {
      globalSettings.enableAutodetectHg();
    } else {
      globalSettings.setHgExecutable(pathSelector.getText());
    }
  }

  public void loadSettings() {
    boolean isAutodetectHg = globalSettings.isAutodetectHg();
    autoRadioButton.setSelected(isAutodetectHg);
    selectRadioButton.setSelected(!isAutodetectHg);
    pathSelector.setEnabled(!isAutodetectHg);
    if (isAutodetectHg) {
      pathSelector.setText(StringUtils.EMPTY);
    } else {
      pathSelector.setText(globalSettings.getHgExecutable());
    }
  }

  private void createUIComponents() {
    pathSelector = new HgSetExecutablePathPanel();
  }
}
