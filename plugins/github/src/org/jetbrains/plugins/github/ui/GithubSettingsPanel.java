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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
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
  private static Logger LOG  = GithubUtil.LOG;

  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JTextPane mySignupTextField;
  private JPanel myPane;
  private JButton myTestButton;
  private JTextField myHostTextField;

  private boolean myPasswordModified;

  public GithubSettingsPanel(final GithubSettings settings) {
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
    myTestButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String password = isPasswordModified() ? getPassword() : settings.getPassword();
        try {
          if (GithubUtil.checkCredentials(ProjectManager.getInstance().getDefaultProject(), getHost(), getLogin(), password)){
            Messages.showInfoMessage(myPane, "Connection successful", "Success");
          } else {
            Messages.showErrorDialog(myPane, "Can't login to " + getHost() + " using given credentials", "Login Failure");
          }
        }
        catch (IOException ex) {
          LOG.info(ex);
          Messages.showErrorDialog(myPane, String.format("Can't login to %s: %s", getHost(), GithubUtil.getErrorTextFromException(ex)),
                                   "Login Failure");
        }
        setPassword(password);
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

  public void setLogin(final String login) {
    myLoginTextField.setText(login);
  }

  public void setPassword(final String password) {
    // Show password as blank if password is empty
    myPasswordField.setText(StringUtil.isEmpty(password) ? null : password);
  }

  public String getLogin() {
    return myLoginTextField.getText().trim();
  }

  public String getPassword() {
    return String.valueOf(myPasswordField.getPassword());
  }

  public void setHost(final String host) {
    myHostTextField.setText(host);
  }

  public String getHost() {
    return myHostTextField.getText().trim();
  }

  public boolean isPasswordModified() {
    return myPasswordModified;
  }

  public void resetPasswordModification() {
    myPasswordModified = false;
  }
}

