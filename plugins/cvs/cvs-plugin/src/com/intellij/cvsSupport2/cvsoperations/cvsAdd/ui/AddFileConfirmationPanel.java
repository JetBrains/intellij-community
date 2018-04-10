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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.util.ui.FileLabel;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class AddFileConfirmationPanel extends AbstractAddFileConfirmationPanel {

  private final JComboBox mySubstitutionComboBox;
  private final FileLabel myFileLabel;
  private final JPanel myPanel;

  public AddFileConfirmationPanel(AddedFileInfo addedFileInfo) {
    super(addedFileInfo);

    myPanel = new JPanel(new GridBagLayout());

    final JPanel panel1 = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.WEST;
    final JLabel label1 = new JLabel(CvsBundle.message("label.add.file.confirmation.keyword.substitution.add.file"));
    panel1.add(label1, constraints);
    myFileLabel = new FileLabel();
    constraints.weightx = 1.0;
    constraints.insets.left = 10;
    panel1.add(myFileLabel, constraints);

    final JLabel label2 = new JLabel(CvsBundle.message("label.add.file.confirmation.keyword.substitution.to.cvs.with"));
    constraints.weightx = 0.0;
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.gridwidth = 3;
    constraints.insets.left = 0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets.bottom = 5;
    myPanel.add(panel1, constraints);
    constraints.gridy = 1;
    constraints.gridx = 0;
    constraints.gridwidth = 1;
    myPanel.add(label2, constraints);
    mySubstitutionComboBox = new JComboBox();
    constraints.gridx = 1;
    myPanel.add(mySubstitutionComboBox, constraints);
    final JLabel label3 = new JLabel(CvsBundle.message("label.add.file.confirmation.keyword.substitution"));
    constraints.gridx = 2;
    constraints.weightx = 1.0;
    myPanel.add(label3, constraints);

    final KeywordSubstitution defaultSubstitution = myAddedFileInfo.getKeywordSubstitution();
    KeywordSubstitutionWrapper.fillComboBox(mySubstitutionComboBox, defaultSubstitution);

    mySubstitutionComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final KeywordSubstitutionWrapper selectedItem = (KeywordSubstitutionWrapper)mySubstitutionComboBox.getSelectedItem();
        myAddedFileInfo.setKeywordSubstitution(selectedItem.getSubstitution());
      }
    });

    init();
  }

  public Component getPanel() {
    return myPanel;
  }

  protected FileLabel getFileLabel() {
    return myFileLabel;
  }
}
