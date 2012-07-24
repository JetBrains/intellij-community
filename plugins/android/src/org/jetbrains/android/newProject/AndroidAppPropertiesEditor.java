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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
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

  private final ModulesProvider myModulesProvider;
  private boolean myApp;

  public AndroidAppPropertiesEditor(String moduleName, ModulesProvider modulesProvider) {
    myModulesProvider = modulesProvider;

    if (moduleName != null) {
      myApplicationNameField.setText(moduleName);
      myPackageNameField.setText(getDefaultPackageNameByModuleName(moduleName));
    }
    myHelloAndroidCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateActivityPanel();
      }
    });
    myPackageNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String message = validatePackageName(!myApp);
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

  public void update(boolean app) {
    myApplicationNameField.setEnabled(app);
    myHelloAndroidCheckBox.setEnabled(app);
    if (app) {
      updateActivityPanel();
    }
    else {
      UIUtil.setEnabled(myActivtiyPanel, app, true);
    }
    myApp = app;
    final String message = validatePackageName(!app);
    myErrorLabel.setText(message);
  }

  @NotNull
  public static String getDefaultPackageNameByModuleName(@NotNull String moduleName) {
    return "com.example." + toIdentifier(moduleName);
  }

  @NotNull
  private static String toIdentifier(@NotNull String s) {
    final StringBuilder result = new StringBuilder();

    for (int i = 0, n = s.length(); i < n; i++) {
      final char c = s.charAt(i);

      if (Character.isJavaIdentifierPart(c)) {
        if (i == 0 && !Character.isJavaIdentifierStart(c)) {
          result.append('_');
        }
        result.append(c);
      }
      else {
        result.append('_');
      }
    }
    return result.toString();
  }

  public void updateActivityPanel() {
    myErrorLabel.setForeground(Color.RED);
    UIUtil.setEnabled(myActivtiyPanel, myHelloAndroidCheckBox.isSelected(), true);
  }

  private String validatePackageName(boolean library) {
    String candidate = myPackageNameField.getText().trim();
    if (candidate.length() == 0) {
      return AndroidBundle.message("specify.package.name.error");
    }
    if (!isValidPackageName(candidate)) {
      return AndroidBundle.message("not.valid.package.name.error", candidate);
    }
    if (!AndroidCommonUtils.contains2Identifiers(candidate)) {
      return AndroidBundle.message("package.name.must.contain.2.ids.error");
    }

    if (!library) {
      for (Module module : myModulesProvider.getModules()) {
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
          final Manifest manifest = facet.getManifest();
          if (manifest != null) {
            final String packageName = manifest.getPackage().getValue();
            if (candidate.equals(packageName)) {
              return "Package name '" + packageName + "' is already used by module '" + module.getName() + "'";
            }
          }
        }
      }
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

  public void validate(boolean library) throws ConfigurationException {
    String message = validatePackageName(library);
    if (message.length() > 0) {
      throw new ConfigurationException(message);
    }
    if (!library) {
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

  public JTextField getApplicationNameField() {
    return myApplicationNameField;
  }

  public JTextField getPackageNameField() {
    return myPackageNameField;
  }
}
