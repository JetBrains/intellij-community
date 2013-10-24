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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

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
  private ComboBox myAuthTypeComboBox;
  private JLabel myPasswordLabel;
  private JLabel myLoginLabel;

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
          if (AUTH_PASSWORD.equals(item)) {
            myPasswordLabel.setText("Password:");
            mySavePasswordCheckBox.setText("Save password");
            myLoginLabel.setVisible(true);
            myLoginTextField.setVisible(true);
          }
          else if (AUTH_TOKEN.equals(item)) {
            myPasswordLabel.setText("Token:");
            mySavePasswordCheckBox.setText("Save token");
            myLoginLabel.setVisible(false);
            myLoginTextField.setVisible(false);
          }
          if (dialog.isShowing()) {
            dialog.pack();
          }
        }
      }
    });

    List<Component> order = new ArrayList<Component>();
    order.add(myHostTextField);
    order.add(myAuthTypeComboBox);
    order.add(myLoginTextField);
    order.add(myPasswordField);
    order.add(mySavePasswordCheckBox);
    myPane.setFocusTraversalPolicyProvider(true);
    myPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(order));
  }

  public JComponent getPanel() {
    return myPane;
  }

  public void setHost(@NotNull String host) {
    myHostTextField.setText(host);
  }

  public void setLogin(@Nullable String login) {
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

  public void lockHost(@NotNull String host) {
    setHost(host);
    myHostTextField.setEnabled(false);
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

  public JComponent getPreferableFocusComponent() {
    return myLoginTextField.isVisible() ? myLoginTextField : myPasswordField;
  }

  @NotNull
  public GithubAuthData getAuthData() {
    Object selected = myAuthTypeComboBox.getSelectedItem();
    if (AUTH_PASSWORD.equals(selected)) return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
    if (AUTH_TOKEN.equals(selected)) return GithubAuthData.createTokenAuth(getHost(), getPassword());
    GithubUtil.LOG.error("GithubLoginPanel illegal selection: anonymous AuthData created", selected.toString());
    return GithubAuthData.createAnonymous(getHost());
  }

  private static class MyFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull private List<Component> myOrder;

    private MyFocusTraversalPolicy(@NotNull List<Component> order) {
      myOrder = order;
    }

    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return ContainerUtil.filter(myOrder, new Condition<Component>() {
        @Override
        public boolean value(Component component) {
          return component.isVisible() && component.isEnabled();
        }
      });
    }
  }
}

