/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.performance.profiler.dtrace;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class SudoAuthDialog extends DialogWrapper implements DocumentListener {
  private JPasswordField myPasswordField;
  private JCheckBox myRememberPasswordBox;

  private char[] myPassword;

  public SudoAuthDialog() {
    super(true);
    setResizable(false);
  }

  public void setup(char[] password) {
    myPassword = password;
    getHelpAction().setEnabled(false);
    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    // top label.
    gb.insets = JBUI.insets(2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    JLabel label = new JLabel("Privileged access is required");
    panel.add(label, gb);

    // user name
    gb.gridy += 1;
    gb.gridwidth = 1;
    gb.weightx = 0;
    gb.fill = GridBagConstraints.NONE;

    label = new JLabel("Password:");
    panel.add(label, gb);

    // user name field
    gb.gridx = 1;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;

    myPasswordField = new JPasswordField();
    panel.add(myPasswordField, gb);
    label.setLabelFor(myPasswordField);

    if (myPassword != null) {
      myPasswordField.setText(String.valueOf(myPassword));
    }
    myPasswordField.selectAll();
    myPasswordField.getDocument().addDocumentListener(this);

    gb.gridy += 1;
    gb.gridx = 0;
    gb.gridwidth = 2;
    gb.weightx = 1;
    gb.fill = GridBagConstraints.HORIZONTAL;
    myRememberPasswordBox = new JCheckBox("Remember");
    panel.add(myRememberPasswordBox, gb);
    gb.gridy += 1;
    panel.add(new JSeparator(), gb);

    myRememberPasswordBox.setSelected(false);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "svn.userNameDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    return myPasswordField != null && myPasswordField.getPassword().length != 0;
  }

  public char[] getPassword() {
    return isOK() && myPasswordField != null ? myPasswordField.getPassword() : null;
  }

  public boolean isRememberPassword() {
    return myRememberPasswordBox.isSelected();
  }
  public void insertUpdate(DocumentEvent e) {
    updateOKButton();
  }

  public void removeUpdate(DocumentEvent e) {
    updateOKButton();
  }

  public void changedUpdate(DocumentEvent e) {
    updateOKButton();
  }

  private void updateOKButton() {
    getOKAction().setEnabled(isOKActionEnabled());
  }
}
