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
