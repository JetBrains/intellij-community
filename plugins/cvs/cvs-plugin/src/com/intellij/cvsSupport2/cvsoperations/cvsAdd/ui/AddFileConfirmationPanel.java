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
