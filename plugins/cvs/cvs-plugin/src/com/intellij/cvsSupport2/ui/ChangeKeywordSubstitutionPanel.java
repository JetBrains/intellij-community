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
