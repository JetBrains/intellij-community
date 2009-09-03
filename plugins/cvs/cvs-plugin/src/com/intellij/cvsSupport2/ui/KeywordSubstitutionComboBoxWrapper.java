package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionListWithSelection;

import javax.swing.*;
import java.util.Iterator;

/**
 * author: lesya
 */

public class KeywordSubstitutionComboBoxWrapper{
  private final JComboBox myComboBox;

  public KeywordSubstitutionComboBoxWrapper(JComboBox comboBox,
                                            String defaultSubst) {
    KeywordSubstitutionListWithSelection values = new KeywordSubstitutionListWithSelection();
    values.select(KeywordSubstitutionWrapper.getValue(defaultSubst));
    myComboBox = comboBox;
    for (Iterator iterator = values.iterator(); iterator.hasNext();) {
      myComboBox.addItem(iterator.next());
    }

    myComboBox.setSelectedItem(values.getSelection());
  }



  public KeywordSubstitutionWrapper getSelection(){
    return (KeywordSubstitutionWrapper)myComboBox.getSelectedItem();
  }
}
