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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagNameFieldOwner;
import com.intellij.ui.DocumentAdapter;
import com.intellij.CvsBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class CvsFieldValidator {
  public static final char[] INVALID_CHARACTERS = new char[]{'`', '$', '.', ',', ':', ';', '@', '\'', ' '};

  private CvsFieldValidator() {}

  public static void reportError(JLabel errorLabel, String message, TagNameFieldOwner tagNameFieldOwner) {
    @NonNls final String text = "<html><font color='red'><b>" + message + "</b></font></html>";
    errorLabel.setText(text);
    if (tagNameFieldOwner != null) {
      tagNameFieldOwner.disableOkAction(message);
    }
  }

  public static void installOn(final TagNameFieldOwner dialog, final JTextField field, final JLabel label) {
    installOn(dialog, field, label, new AbstractButton[0]);
    field.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        checkTagNameField(dialog, field, label);
      }
    });
    checkTagNameField(dialog, field, label);
  }

  public static void installOn(final TagNameFieldOwner dialog,
                               final JTextField field,
                               final JLabel label,
                               AbstractButton[] buttons) {
    field.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        checkTagNameField(dialog, field, label);
      }
    });

    for (AbstractButton button : buttons) {
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          checkTagNameField(dialog, field, label);
        }
      });
    }

    checkTagNameField(dialog, field, label);
  }

  private static void checkTagNameField(TagNameFieldOwner dialog, JTextField field, JLabel label) {
    if (!dialog.tagFieldIsActive()) {
      label.setText(" ");
      dialog.enableOkAction();
    }
    else if (checkField(field, new JTextField[0], true, label, dialog)) {
      dialog.enableOkAction();
    }
    else {
      field.requestFocus();
    }
  }

  public static boolean checkField(JTextField field,
                                   JTextField[] shouldDifferFrom,
                                   boolean shouldStartFromLetter,
                                   JLabel errorLabel, TagNameFieldOwner tagNameFieldOwner) {
    final String text = field.getText().trim();
    if (text.isEmpty()) {
      reportError(errorLabel, CvsBundle.message("error.message.field.cannot.be.empty"), tagNameFieldOwner);
      return false;
    }

    for (char invalidCharacter : INVALID_CHARACTERS) {
      if (text.indexOf(invalidCharacter) != -1) {
        reportError(errorLabel, CvsBundle.message("error.message.field.contains.invalid.characters"), tagNameFieldOwner);
        return false;
      }
    }

    for (JTextField jTextField : shouldDifferFrom) {
      if (jTextField == field) continue;
      if (jTextField.getText().trim().equals(text)) {
        reportError(errorLabel, CvsBundle.message("error.message.duplicate.field.value"), tagNameFieldOwner);
        return false;
      }
    }

    if (shouldStartFromLetter && !Character.isLetter(text.charAt(0))) {
      reportError(errorLabel, CvsBundle.message("error.message.field.value.must.start.with.a.letter"), tagNameFieldOwner);
      return false;
    }

    errorLabel.setText(" ");
    return true;
  }
}
