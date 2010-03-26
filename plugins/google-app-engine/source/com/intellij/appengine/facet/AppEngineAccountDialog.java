package com.intellij.appengine.facet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.PasswordUtil;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineAccountDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JCheckBox myRememberPasswordCheckBox;
  private JPasswordField myPasswordField;
  private JTextField myUserEmailField;
  private final AppEngineFacetConfiguration myConfiguration;

  public AppEngineAccountDialog(Project project, AppEngineFacetConfiguration configuration) {
    super(project);
    setTitle("AppEngine Account");
    myConfiguration = configuration;
    myUserEmailField.setText(myConfiguration.getUserEmail());
    myPasswordField.setText(myConfiguration.getPassword());
    init();
  }

  public String getEmail() {
    return myUserEmailField.getText();
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUserEmailField;
  }

  @Override
  protected void doOKAction() {
    myConfiguration.setUserEmail(getEmail());
    if (myRememberPasswordCheckBox.isSelected()) {
      myConfiguration.setEncryptedPassword(PasswordUtil.encodePassword(getPassword()));
    }
    super.doOKAction();
  }
}
