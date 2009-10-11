/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class ChangeKeywordSubstitutionPanel {
  private JComboBox myKeywordSubstitutionComboBox;
  private JCheckBox myChangeCheckbox;
  private final KeywordSubstitutionComboBoxWrapper myComboBoxWrapper;
  private JPanel myPanel;

  public ChangeKeywordSubstitutionPanel(String defaultSubst) {
    myComboBoxWrapper = new KeywordSubstitutionComboBoxWrapper(myKeywordSubstitutionComboBox, defaultSubst);
    myChangeCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setEnabling();
      }
    });

    setEnabling();
  }

  private void setEnabling() {
    myKeywordSubstitutionComboBox.setEnabled(myChangeCheckbox.isSelected());
    if (myKeywordSubstitutionComboBox.isEnabled() && (myKeywordSubstitutionComboBox.getSelectedIndex() == -1)){
      myKeywordSubstitutionComboBox.setSelectedIndex(0);
    }
  }

  public Component getPanel() {
    return myPanel;
  }

  public String getKeywordSubstitution() {
    if (!myChangeCheckbox.isSelected()) {
      //noinspection HardCodedStringLiteral
      return "NONE";
    }
    else {
      return myComboBoxWrapper.getSelection().getStringRepresentation();
    }
  }
}
