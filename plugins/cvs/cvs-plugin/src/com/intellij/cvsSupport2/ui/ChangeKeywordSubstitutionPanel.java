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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class ChangeKeywordSubstitutionPanel {

  private final JComboBox myKeywordSubstitutionComboBox;
  private final JCheckBox myChangeCheckbox;
  private final JPanel myPanel;

  public ChangeKeywordSubstitutionPanel(KeywordSubstitution defaultSubstitution) {
    myPanel = new JPanel(new GridBagLayout());
    myChangeCheckbox = new JCheckBox(CvsBundle.message("checkbox.change.keyword.substitution.to"));
    myKeywordSubstitutionComboBox = new JComboBox();
    KeywordSubstitutionWrapper.fillComboBox(myKeywordSubstitutionComboBox, defaultSubstitution);

    myChangeCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setEnabling();
      }
    });

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    myPanel.add(myChangeCheckbox, constraints);
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    myPanel.add(myKeywordSubstitutionComboBox, constraints);
    setEnabling();
  }

  private void setEnabling() {
    myKeywordSubstitutionComboBox.setEnabled(myChangeCheckbox.isSelected());
    if (myKeywordSubstitutionComboBox.getSelectedIndex() == -1){
      myKeywordSubstitutionComboBox.setSelectedIndex(0);
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public KeywordSubstitution getKeywordSubstitution() {
    if (!myChangeCheckbox.isSelected()) {
      return null;
    }
    return ((KeywordSubstitutionWrapper)myKeywordSubstitutionComboBox.getSelectedItem()).getSubstitution();
  }
}
