package com.intellij.cvsSupport2.ui;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author Jeka
 */
public class PasswordPromptDialog extends DialogWrapper {
  private JPasswordField myPasswordField;
  private String myPrompt;

  public PasswordPromptDialog(String prompt) {
    super(true);
    setTitle("CVS Login");
    myPrompt = prompt;
    init();
  }

  protected JComponent createNorthPanel() {
    return new JLabel(myPrompt);
  }

  protected JComponent createCenterPanel() {
    myPasswordField = new JPasswordField();
    return myPasswordField;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }
}
