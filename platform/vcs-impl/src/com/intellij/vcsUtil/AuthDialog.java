// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.vcsUtil;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.config.PasswordSafeSettings;
import com.intellij.ide.passwordSafe.impl.PasswordSafeImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.AuthenticationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AuthDialog extends DialogWrapper {
  private final AuthenticationPanel authPanel;

  /**
   * If password if prefilled, it is expected to continue remembering it.
   * On the other hand, if password saving is disabled, the checkbox is not shown.
   * In other cases, {@code rememberByDefault} is used.
   */
  public AuthDialog(@NotNull Project project, @NotNull String title, @Nullable String description, @Nullable String login, @Nullable String password, boolean rememberByDefault) {
    super(project, false);
    setTitle(title);
    Boolean rememberPassword = decideOnShowRememberPasswordOption(password, rememberByDefault);
    authPanel = new AuthenticationPanel(description, login, password, rememberPassword);
    init();
  }

  @Nullable
  private static Boolean decideOnShowRememberPasswordOption(@Nullable String password, boolean rememberByDefault) {
    final PasswordSafeImpl passwordSafe = (PasswordSafeImpl)PasswordSafe.getInstance();
    // if password saving is disabled, don't show the checkbox.
    if (passwordSafe.getSettings().getProviderType().equals(PasswordSafeSettings.ProviderType.DO_NOT_STORE)) {
      return null;
    }
    // if password is prefilled, it is expected to continue remembering it.
    if (!StringUtil.isEmptyOrSpaces(password)) {
      return true;
    }
    return rememberByDefault;
  }

  @Override
  protected JComponent createCenterPanel() {
    return authPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return authPanel.getPreferredFocusedComponent();
  }

  @NotNull
  public String getUsername() {
    return authPanel.getLogin();
  }

  @NotNull
  public String getPassword() {
    return String.valueOf(authPanel.getPassword());
  }

  public boolean isRememberPassword() {
    return authPanel.isRememberPassword();
  }
  
}
