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
package org.jetbrains.android.newProject;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAppPropertiesEditor {
  private JTextField myApplicationNameField;
  private JTextField myPackageNameField;
  private JCheckBox myHelloAndroidCheckBox;
  private JPanel myActivtiyPanel;
  private JTextField myActivityNameField;
  private JLabel myErrorLabel;
  private JPanel myContentPanel;

  private final String myModuleName;

  public AndroidAppPropertiesEditor(String moduleName) {
    myModuleName = moduleName;
    myApplicationNameField.setText(moduleName);
    myHelloAndroidCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateActivityPanel();
      }
    });
    myPackageNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String message = validatePackageName();
        myErrorLabel.setText(message);
      }
    });
    myActivityNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String message = validateActivityName();
        myErrorLabel.setText(message);
      }
    });
  }

  public void updateActivityPanel() {
    myErrorLabel.setForeground(Color.RED);
    UIUtil.setEnabled(myActivtiyPanel, myHelloAndroidCheckBox.isSelected(), true);
  }

  private String validatePackageName() {
    String candidate = myPackageNameField.getText().trim();
    if (candidate.length() == 0) {
      return AndroidBundle.message("specify.package.name.error");
    }
    if (!isValidPackageName(candidate)) {
      return AndroidBundle.message("not.valid.package.name.error", candidate);
    }
    if (!AndroidUtils.contains2Ids(candidate)) {
      return AndroidBundle.message("package.name.must.contain.2.ids.error");
    }
    return "";
  }

  private String validateActivityName() {
    String candidate = myActivityNameField.getText().trim();
    if (!isIdentifier(candidate)) {
      return AndroidBundle.message("not.valid.acvitiy.name.error", candidate);
    }
    return "";
  }

  public static boolean isValidPackageName(@NotNull String name) {
    int index = 0;
    while (true) {
      int index1 = name.indexOf('.', index);
      if (index1 < 0) index1 = name.length();
      if (!isIdentifier(name.substring(index, index1))) return false;
      if (index1 == name.length()) return true;
      index = index1 + 1;
    }
  }

  private static boolean isIdentifier(@NotNull String candidate) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Lexer lexer = new JavaLexer(LanguageLevel.JDK_1_3);
    lexer.start(candidate);
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
    lexer.advance();
    return lexer.getTokenType() == null;
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public void validate(boolean testProject) throws ConfigurationException {
    String message = validatePackageName();
    if (message.length() > 0) {
      throw new ConfigurationException(message);
    }
    if (!testProject) {
      message = validateActivityName();
      if (message.length() > 0) {
        throw new ConfigurationException(message);
      }
    }
  }

  public String getActivityName() {
    return myHelloAndroidCheckBox.isSelected() ? myActivityNameField.getText().trim() : "";
  }

  public String getPackageName() {
    return myPackageNameField.getText().trim();
  }

  public String getApplicationName() {
    return myApplicationNameField.getText().trim();
  }

  public JCheckBox getHelloAndroidCheckBox() {
    return myHelloAndroidCheckBox;
  }

  public JTextField getApplicationNameField() {
    return myApplicationNameField;
  }

  public JPanel getActivtiyPanel() {
    return myActivtiyPanel;
  }
}
