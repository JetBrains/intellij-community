/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import com.intellij.CommonBundle;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class AppEngineAccountDialog extends DialogWrapper {
  public static final String PASSWORD_KEY = "GOOGLE_APP_ENGINE_PASSWORD";
  private static final String EMAIL_KEY = "GOOGLE_APP_ENGINE_ACCOUNT_EMAIL";
  private JPanel myMainPanel;
  private JCheckBox myRememberPasswordCheckBox;
  private JPasswordField myPasswordField;
  private JTextField myUserEmailField;
  private final Project myProject;

  public AppEngineAccountDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    setTitle("AppEngine Account");
    myUserEmailField.setText(StringUtil.notNullize(getStoredEmail(project)));
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

  @Nullable
  public static String getStoredEmail(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getValue(EMAIL_KEY);
  }

  @Override
  protected void doOKAction() {
    final String email = getEmail();
    PropertiesComponent.getInstance(myProject).setValue(EMAIL_KEY, email);
    if (myRememberPasswordCheckBox.isSelected()) {
      try {
        PasswordSafe.getInstance().storePassword(myProject, AppEngineAccountDialog.class, getPasswordKey(email), getPassword());
      }
      catch (PasswordSafeException e) {
        Messages.showErrorDialog(myProject, "Cannot store password: " + e.getMessage(), CommonBundle.getErrorTitle());
        return;
      }
    }
    super.doOKAction();
  }

  private static String getPasswordKey(String email) {
    return PASSWORD_KEY + "_" + email;
  }

  @Nullable
  public static String getStoredPassword(Project project, String email) throws PasswordSafeException {
    if (StringUtil.isEmpty(email)) {
      return null;
    }

    return PasswordSafe.getInstance().getPassword(project, AppEngineAccountDialog.class, getPasswordKey(email));
  }
}
