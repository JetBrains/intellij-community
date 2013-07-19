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
package org.jetbrains.plugins.github.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginPanel {
  private JPanel myPane;
  private JTextField myHostTextField;
  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JTextPane mySignupTextField;
  private JCheckBox mySavePasswordCheckBox;
  private JComboBox myAuthTypeComboBox;
  private JLabel myPasswordLabel;

  private final static String AUTH_PASSWORD = "Password";
  private final static String AUTH_TOKEN = "Token";

  public GithubLoginPanel(final GithubLoginDialog dialog) {
    DocumentListener listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        dialog.clearErrors();
      }
    };
    myLoginTextField.getDocument().addDocumentListener(listener);
    myPasswordField.getDocument().addDocumentListener(listener);
    mySignupTextField.setText("<html>Do not have an account at github.com? <a href=\"https://github.com\">Sign up</a>.</html>");
    mySignupTextField.setMargin(new Insets(5, 0, 0, 0));
    mySignupTextField.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(final HyperlinkEvent e) {
        BrowserUtil.browse(e.getURL());
      }
    });
    mySignupTextField.setBackground(UIUtil.TRANSPARENT_COLOR);
    mySignupTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));

    myAuthTypeComboBox.addItem(AUTH_PASSWORD);
    myAuthTypeComboBox.addItem(AUTH_TOKEN);

    myAuthTypeComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          String item = e.getItem().toString();
          myPasswordLabel.setText(item + ":");
          mySavePasswordCheckBox.setText("Save " + item.toLowerCase());
        }
      }
    });
  }

  public JComponent getPanel() {
    return myPane;
  }

  public void setHost(@NotNull String host) {
    myHostTextField.setText(host);
  }

  public void setLogin(@NotNull String login) {
    myLoginTextField.setText(login);
  }

  public void setAuthType(@NotNull GithubAuthData.AuthType type) {
    switch (type) {
      case BASIC:
        myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
        break;
      case TOKEN:
        myAuthTypeComboBox.setSelectedItem(AUTH_TOKEN);
        break;
      case ANONYMOUS:
        myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
    }
  }

  public void lockAuthType(@NotNull GithubAuthData.AuthType type) {
    setAuthType(type);
    myAuthTypeComboBox.setEnabled(false);
  }

  public void setSavePasswordSelected(boolean savePassword) {
    mySavePasswordCheckBox.setSelected(savePassword);
  }

  public void setSavePasswordVisibleEnabled(boolean visible) {
    mySavePasswordCheckBox.setVisible(visible);
    mySavePasswordCheckBox.setEnabled(visible);
  }

  @NotNull
  public String getHost() {
    return myHostTextField.getText().trim();
  }

  @NotNull
  public String getLogin() {
    return myLoginTextField.getText().trim();
  }

  @NotNull
  private String getPassword() {
    return String.valueOf(myPasswordField.getPassword());
  }

  public boolean isSavePasswordSelected() {
    return mySavePasswordCheckBox.isSelected();
  }

  public JComponent getPreferrableFocusComponent() {
    return myLoginTextField;
  }

  @NotNull
  public GithubAuthData getAuthData() {
    Object selected = myAuthTypeComboBox.getSelectedItem();
    if (AUTH_PASSWORD.equals(selected)) return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
    if (AUTH_TOKEN.equals(selected)) return GithubAuthData.createTokenAuth(getHost(), getPassword());
    GithubUtil.LOG.error("GithubLoginPanel illegal selection: anonymous AuthData created", selected.toString());
    return GithubAuthData.createAnonymous(getHost());
  }
}

