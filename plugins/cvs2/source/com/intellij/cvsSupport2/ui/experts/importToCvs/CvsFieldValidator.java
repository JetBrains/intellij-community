package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagNameFieldOwner;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class CvsFieldValidator {
  public static final char[] INVALID_CHARACTERS = new char[]{'`', '$', '.', ',', ':', ';', '@', '\'', ' '};

  private static void reportError(JLabel errorLabel, String message, TagNameFieldOwner tagNameFieldOwner) {
    @NonNls final String text = "<html><font color='red'><b>" + message + "</b></font></html>";
    errorLabel.setText(text);
    if (tagNameFieldOwner != null) {
      tagNameFieldOwner.disableOkAction(message);
    }
  }

  public static void installOn(final TagNameFieldOwner dialog, final JTextField field, final JLabel label) {
    installOn(dialog, field, label, new AbstractButton[0]);
    field.getDocument().addDocumentListener(new DocumentAdapter() {
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
      public void textChanged(DocumentEvent event) {
        checkTagNameField(dialog, field, label);
      }
    });

    for (int i = 0; i < buttons.length; i++) {
      AbstractButton button = buttons[i];
      button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          checkTagNameField(dialog, field, label);
        }
      });
    }

    checkTagNameField(dialog, field, label);
  }

  private static void checkTagNameField(TagNameFieldOwner dialog, JTextField field, JLabel label) {
    if (!dialog.tagFieldIsActive()) {
      label.setText("");
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
                                   JLabel errorMessage, TagNameFieldOwner tagNameFieldOwner) {
    String text = field.getText().trim();
    if (text.length() == 0) {
      reportError(errorMessage, com.intellij.CvsBundle.message("error.message.field.cannot.be.empty"), tagNameFieldOwner);
      return false;
    }

    for (int i = 0; i < INVALID_CHARACTERS.length; i++) {
      char invalidCharacter = INVALID_CHARACTERS[i];
      if (text.indexOf(invalidCharacter) != -1) {
        reportError(errorMessage, com.intellij.CvsBundle.message("error.message.field.contains.invalid.characters"), tagNameFieldOwner);
        return false;
      }
    }

    for (int i = 0; i < shouldDifferFrom.length; i++) {
      JTextField jTextField = shouldDifferFrom[i];
      if (jTextField == field) continue;
      if (jTextField.getText().trim().equals(text)) {
        reportError(errorMessage, com.intellij.CvsBundle.message("error.message.duplicate.field.value"), tagNameFieldOwner);
        return false;
      }
    }


    if (shouldStartFromLetter && !Character.isLetter(text.charAt(0))) {
      reportError(errorMessage, com.intellij.CvsBundle.message("error.message.field.value.must.start.with.a.letter"), tagNameFieldOwner);
      return false;
    }

    errorMessage.setText("");
    return true;
  }


}
