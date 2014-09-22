/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.appengine.cloud;

import com.intellij.appengine.facet.AppEngineAccountDialog;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
* @author nik
*/
public class AppEngineCloudConfigurable implements Configurable {
  private final AppEngineServerConfiguration myConfiguration;
  @Nullable private final Project myProject;
  private JTextField myEmailField;
  private JPasswordField myPasswordField;
  private JBRadioButton myPasswordLoginButton;
  private JBRadioButton myOAuthLoginButton;
  private JPanel myMainPanel;
  private JCheckBox myRememberPasswordCheckBox;

  public AppEngineCloudConfigurable(@NotNull AppEngineServerConfiguration configuration, @Nullable Project project) {
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
    myEmailField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateControls();
      }
    });
    updateControls();
  }

  private void updateControls() {
    boolean passwordLogin = myPasswordLoginButton.isSelected();
    myEmailField.setEnabled(passwordLogin);
    myPasswordField.setEnabled(passwordLogin);
    myRememberPasswordCheckBox.setEnabled(passwordLogin);
  }

  public String getEmail() {
    return StringUtil.nullize(myEmailField.getText(), true);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Google App Engine Account";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  public void reset() {
    String email = myConfiguration.getEmail();
    myEmailField.setText(StringUtil.notNullize(email));
    if (myConfiguration.isOAuth2()) {
      myOAuthLoginButton.setSelected(true);
    }
    else {
      myPasswordLoginButton.setSelected(true);
    }
    updateControls();
  }

  public void apply() {
    String email = getEmail();
    myConfiguration.setEmail(email);
    myConfiguration.setOAuth2(isOAuth2());
    String password = getPassword();
    if (myRememberPasswordCheckBox.isSelected() && !StringUtil.isEmpty(email) && !password.isEmpty()) {
      AppEngineAccountDialog.storePassword(email, password, myProject);
    }
  }

  public boolean isOAuth2() {
    return myOAuthLoginButton.isSelected();
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }

  public boolean isModified() {
    return !Comparing.strEqual(getEmail(), myConfiguration.getEmail()) || myConfiguration.isOAuth2() != isOAuth2();
  }

  @Override
  public void disposeUIResources() {
  }
}
