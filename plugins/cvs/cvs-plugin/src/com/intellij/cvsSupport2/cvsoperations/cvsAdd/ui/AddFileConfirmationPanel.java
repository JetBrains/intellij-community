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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.ui.KeywordSubstitutionComboBoxWrapper;
import com.intellij.util.ui.FileLabel;
import com.intellij.util.ui.FileLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class AddFileConfirmationPanel extends AbstractAddFileConfirmationPanel {
  private JComboBox mySubstitutionComboBox;
  private FileLabel myFileLabel;
  private JPanel myPanel;


  public AddFileConfirmationPanel(AddedFileInfo addedFileInfo) {
    super(addedFileInfo);

    KeywordSubstitutionWrapper defaultSubst = (KeywordSubstitutionWrapper)myAddedFileInfo.getKeywordSubstitutionsWithSelection()
      .getSelection();
    final KeywordSubstitutionComboBoxWrapper wrapper = new KeywordSubstitutionComboBoxWrapper(mySubstitutionComboBox,
                                                                                              defaultSubst.getStringRepresentation()
    );


    mySubstitutionComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myAddedFileInfo.setKeywordSubstitution(wrapper.getSelection().getSubstitution());
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
