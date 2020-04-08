// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.cloud;

import com.intellij.appengine.facet.AppEngineAccountDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.RemoteServerConfigurable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AppEngineCloudConfigurable extends RemoteServerConfigurable implements Configurable {
  public static final String EMAIL_KEY = "GOOGLE_APP_ENGINE_ACCOUNT_EMAIL";
  private final AppEngineServerConfiguration myConfiguration;
  @Nullable private final Project myProject;
  private JTextField myEmailField;
  private JBPasswordField myPasswordField;
  private JBRadioButton myPasswordLoginButton;
  private JBRadioButton myOAuthLoginButton;
  private JPanel myMainPanel;
  private JCheckBox myRememberPasswordCheckBox;
  private final boolean myAlwaysRememberPassword;

  public AppEngineCloudConfigurable(@NotNull AppEngineServerConfiguration configuration,
                                    @Nullable Project project, boolean alwaysRememberPassword) {
    myConfiguration = configuration;
    myProject = project;
    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        updateControls();
      }
    };
    myPasswordLoginButton.addActionListener(actionListener);
    myOAuthLoginButton.addActionListener(actionListener);
    DocumentListener documentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateControls();
      }
    };
    myEmailField.getDocument().addDocumentListener(documentListener);
    myPasswordField.getDocument().addDocumentListener(documentListener);
    myAlwaysRememberPassword = alwaysRememberPassword;
    updateControls();
  }

  private void updateControls() {
    boolean passwordLogin = myPasswordLoginButton.isSelected();
    myEmailField.setEnabled(passwordLogin);
    myPasswordField.setEnabled(passwordLogin);
    myRememberPasswordCheckBox.setEnabled(passwordLogin);
    if (passwordLogin && myAlwaysRememberPassword && !getPassword().isEmpty()) {
      myRememberPasswordCheckBox.setSelected(true);
      myRememberPasswordCheckBox.setEnabled(false);
    }
  }

  public String getEmail() {
    return StringUtil.nullize(myEmailField.getText(), true);
  }

  @Override
  public boolean canCheckConnection() {
    //currently our App Engine implementation actually doesn't connect to the cloud so it makes no sense to show connection status in 'Settings'
    return false;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeBundle.message("configurable.AppEngineCloudConfigurable.display.name");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void reset() {
    String email = myConfiguration.getEmail();
    if (email == null) {
      email = getOldEmail();
    }
    myEmailField.setText(StringUtil.notNullize(email));
    if (myConfiguration.isOAuth2()) {
      myOAuthLoginButton.setSelected(true);
    }
    else {
      myPasswordLoginButton.setSelected(true);
    }
    myRememberPasswordCheckBox.setSelected(myConfiguration.isPasswordStored());
    myPasswordField.setPasswordIsStored(myConfiguration.isPasswordStored());
    updateControls();
  }

  @Nullable
  private static String getOldEmail() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      String value = PropertiesComponent.getInstance(project).getValue(EMAIL_KEY);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static void removeOldEmail(@NotNull String email) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      String value = PropertiesComponent.getInstance(project).getValue(EMAIL_KEY);
      if (email.equals(value)) {
        PropertiesComponent.getInstance(project).unsetValue(EMAIL_KEY);
      }
    }
  }

  @Override
  public void apply() {
    String email = getEmail();
    if (email != null) {
      removeOldEmail(email);
    }
    myConfiguration.setEmail(email);
    myConfiguration.setOAuth2(isOAuth2());
    String password = getPassword();
    if (myRememberPasswordCheckBox.isSelected() && !StringUtil.isEmpty(email) && !password.isEmpty()) {
      AppEngineAccountDialog.storePassword(email, password);
      myConfiguration.setPasswordStored(true);
    }
    else {
      myConfiguration.setPasswordStored(false);
    }
  }

  public boolean isOAuth2() {
    return myOAuthLoginButton.isSelected();
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }

  @Override
  public boolean isModified() {
    return !Comparing.strEqual(getEmail(), myConfiguration.getEmail()) || myConfiguration.isOAuth2() != isOAuth2()
           || myRememberPasswordCheckBox.isSelected() != myConfiguration.isPasswordStored()
           || myRememberPasswordCheckBox.isSelected() && !getPassword().isEmpty();
  }
}
