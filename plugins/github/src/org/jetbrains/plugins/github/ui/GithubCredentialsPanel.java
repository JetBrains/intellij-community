/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.util.*;
import org.jetbrains.plugins.github.util.GithubAuthData.AuthType;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;

public class GithubCredentialsPanel extends JPanel {
  private static final Logger LOG = GithubUtil.LOG;

  private JTextField myHostTextField;
  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JPasswordField myTokenField;

  private ComboBox<Layout> myAuthTypeComboBox;
  private JButton myCreateTokenButton;
  private JButton myTestButton;

  private JBLabel myAuthTypeLabel;
  private JTextPane mySignupTextField;
  private JPanel myPane;
  private JPanel myCardPanel;

  public GithubCredentialsPanel(@NotNull Project project) {
    super(new BorderLayout());
    add(myPane, BorderLayout.CENTER);

    mySignupTextField.setEditorKit(UIUtil.getHTMLEditorKit());
    mySignupTextField.setText("<html>Do not have an account at github.com? <a href=\"https://github.com\">Sign up</a></html>");
    mySignupTextField.setBackground(UIUtil.TRANSPARENT_COLOR);
    mySignupTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));
    mySignupTextField.setMargin(JBUI.insetsTop(5));
    mySignupTextField.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        BrowserUtil.browse(e.getURL());
      }
    });

    myAuthTypeLabel.setBorder(JBUI.Borders.emptyLeft(10));
    myAuthTypeComboBox.addItem(Layout.TOKEN);
    myAuthTypeComboBox.addItem(Layout.PASSWORD);


    myTestButton.addActionListener(e -> testAuthData(project));
    myCreateTokenButton.addActionListener(e -> generateToken(project));

    myAuthTypeComboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        Layout item = (Layout)e.getItem();
        CardLayout cardLayout = (CardLayout)myCardPanel.getLayout();
        cardLayout.show(myCardPanel, item.getCard());
      }
    });
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

  @NotNull
  private String getToken() {
    return String.valueOf(myTokenField.getPassword());
  }

  @NotNull
  public AuthType getAuthType() {
    Layout selected = (Layout)myAuthTypeComboBox.getSelectedItem();
    if (selected == Layout.PASSWORD) return AuthType.BASIC;
    if (selected == Layout.TOKEN) return AuthType.TOKEN;
    LOG.error("GithubSettingsPanel: illegal selection - " + selected);
    return AuthType.TOKEN;
  }

  @NotNull
  public GithubAuthData getAuthData() {
    AuthType type = getAuthType();
    switch (type) {
      case BASIC:
        return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
      case TOKEN:
        return GithubAuthData.createTokenAuth(getHost(), StringUtil.trim(getToken()));
      default:
        throw new IllegalStateException();
    }
  }

  public void setHost(@NotNull String host) {
    myHostTextField.setText(host);
  }

  public void setLogin(@Nullable String login) {
    myLoginTextField.setText(login);
  }

  public void setPassword(@NotNull String password) {
    myPasswordField.setText(password);
  }

  public void setToken(@NotNull String token) {
    myTokenField.setText(token);
  }

  public void setAuthType(@NotNull GithubAuthData.AuthType type) {
    if (type == GithubAuthData.AuthType.BASIC) {
      myAuthTypeComboBox.setSelectedItem(Layout.PASSWORD);
    }
    else {
      myAuthTypeComboBox.setSelectedItem(Layout.TOKEN);
    }
  }

  public void setAuthData(@NotNull GithubAuthData authData) {
    AuthType type = authData.getAuthType();
    setAuthType(type);
    setHost(authData.getHost());
    if (type == AuthType.BASIC) {
      GithubAuthData.BasicAuth basicAuth = authData.getBasicAuth();
      assert basicAuth != null;
      setLogin(basicAuth.getLogin());
      setPassword(basicAuth.getPassword());
    }
    if (type == AuthType.TOKEN) {
      GithubAuthData.TokenAuth tokenAuth = authData.getTokenAuth();
      assert tokenAuth != null;
      setToken(tokenAuth.getToken());
    }
  }

  public void lockAuthType(@NotNull AuthType type) {
    setAuthType(type);
    myAuthTypeComboBox.setEnabled(false);
  }

  public void lockHost(@NotNull String host) {
    setHost(host);
    myHostTextField.setEnabled(false);
  }

  public void setTestButtonVisible(boolean visible) {
    myTestButton.setVisible(visible);
  }


  private void testAuthData(@NotNull Project project) {
    try {
      GithubAuthData auth = getAuthData();
      GithubUser user = GithubUtil.computeValueInModalIO(project, "Access to GitHub", indicator ->
        GithubUtil.checkAuthData(project, new GithubAuthDataHolder(auth), indicator));

      if (AuthType.TOKEN.equals(auth.getAuthType())) {
        GithubNotifications.showInfoDialog(myPane, "Success", "Connection successful for user " + user.getLogin());
      }
      else {
        GithubNotifications.showInfoDialog(myPane, "Success", "Connection successful");
      }
    }
    catch (GithubAuthenticationException ex) {
      GithubNotifications.showErrorDialog(myPane, "Login Failure", "Can't login using given credentials: ", ex);
    }
    catch (IOException ex) {
      GithubNotifications.showErrorDialog(myPane, "Login Failure", "Can't login: ", ex);
    }
  }

  private void generateToken(@NotNull Project project) {
    try {
      String newToken = GithubUtil.computeValueInModalIO(project, "Access to GitHub", indicator ->
        GithubUtil.runTask(project, GithubAuthDataHolder.createFromSettings(), indicator, AuthLevel.basicOnetime(getHost()), connection ->
          GithubApiUtil.getMasterToken(connection, "IntelliJ plugin")));
      myTokenField.setText(newToken);
    }
    catch (IOException ex) {
      GithubNotifications.showErrorDialog(myPane, "Can't Create API Token", ex);
    }
  }


  private enum Layout {
    PASSWORD("Password"),
    TOKEN("Token");

    @NotNull private final String myCard;

    Layout(@NotNull String card) {
      myCard = card;
    }

    @NotNull
    public String getCard() {
      return myCard;
    }

    @Override
    public String toString() {
      return myCard;
    }
  }
}
