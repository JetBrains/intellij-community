/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubSettings;
import org.jetbrains.plugins.github.GithubUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubSettingsPanel {
  private static final String DEFAULT_PASSWORD_TEXT = "************";
  private final static String AUTH_PASSWORD = "Password";
  private final static String AUTH_TOKEN = "Token";

  private static final Logger LOG = GithubUtil.LOG;

  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JTextPane mySignupTextField;
  private JPanel myPane;
  private JButton myTestButton;
  private JTextField myHostTextField;
  private JComboBox myAuthTypeComboBox;

  private boolean myPasswordModified;
  private GithubSettings mySettings;

  public GithubSettingsPanel(@NotNull final GithubSettings settings) {
    mySettings = settings;
    mySignupTextField.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(final HyperlinkEvent e) {
        BrowserUtil.browse(e.getURL());
      }
    });
    mySignupTextField.setText(
      "<html>Do not have an account at github.com? <a href=\"https://github.com\">" + "Sign up" + "</a></html>");
    mySignupTextField.setBackground(myPane.getBackground());
    mySignupTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myAuthTypeComboBox.addItem(AUTH_PASSWORD);
    myAuthTypeComboBox.addItem(AUTH_TOKEN);

    reset();

    myTestButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        GithubAuthData auth = isPasswordModified() ? getAuthData() : mySettings.getAuthData();
        try {
          if (GithubUtil.checkAuthData(auth)) {
            Messages.showInfoMessage(myPane, "Connection successful", "Success");
          }
          else {
            Messages.showErrorDialog(myPane, "Can't login to " + getHost() + " using given credentials", "Login Failure");
          }
        }
        catch (IOException ex) {
          LOG.info(ex);
          Messages.showErrorDialog(myPane, String.format("Can't login to %s: %s", getHost(), GithubUtil.getErrorTextFromException(ex)),
                                   "Login Failure");
        }
        //TODO: do we really need "setPassword(password);" here?
      }
    });

    myPasswordField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myPasswordModified = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myPasswordModified = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        myPasswordModified = true;
      }
    });
  }

  public JComponent getPanel() {
    return myPane;
  }

  @NotNull
  public String getHost() {
    return myHostTextField.getText().trim();
  }

  @NotNull
  public String getLogin() {
    return myLoginTextField.getText().trim();
  }

  public void setHost(@NotNull final String host) {
    myHostTextField.setText(host);
  }

  public void setLogin(@NotNull final String login) {
    myLoginTextField.setText(login);
  }

  @NotNull
  private String getPassword() {
    return String.valueOf(myPasswordField.getPassword());
  }

  private void setPassword(@NotNull final String password) {
    // Show password as blank if password is empty
    myPasswordField.setText(StringUtil.isEmpty(password) ? null : password);
  }

  public void setAuthType(@NotNull final GithubAuthData.AuthType type) {
    switch (type) {
      case BASIC:
        myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
        break;
      case TOKEN:
        myAuthTypeComboBox.setSelectedItem(AUTH_TOKEN);
        break;
      case ANONYMOUS:
      default:
        myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
    }
  }

  @NotNull
  public GithubAuthData getAuthData() {
    Object selected = myAuthTypeComboBox.getSelectedItem();
    if (selected == AUTH_PASSWORD) return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
    if (selected == AUTH_TOKEN) return GithubAuthData.createTokenAuth(getHost(), getLogin(), getPassword());
    LOG.error("GithubSettingsPanel: illegal selection: anonymous AuthData created");
    return GithubAuthData.createAnonymous(getHost());
  }

  public void reset() {
    String login = mySettings.getLogin();
    setHost(mySettings.getHost());
    setLogin(login);
    setPassword(login.isEmpty() ? "" : DEFAULT_PASSWORD_TEXT);
    setAuthType(mySettings.getAuthType());
    resetPasswordModification();
  }

  public boolean isPasswordModified() {
    return myPasswordModified;
  }

  public void resetPasswordModification() {
    myPasswordModified = false;
  }
}

