// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

public final class FormUtils {

  private FormUtils() {}

  public static String getFieldValue(JTextField field, boolean check) {
    final String value = field.getText().trim();
    if (check && value.isEmpty()) {
      throw new InputException(CvsBundle.message("error.message.value.cannot.be.empty", getLabelText(field)), field);
    }
    return value;
  }

  public static String getFieldValue(TextFieldWithBrowseButton field, boolean check) {
    final String value = field.getText().trim();
    if (check && value.isEmpty()) {
      throw new InputException(CvsBundle.message("error.message.value.cannot.be.empty", getLabelText(field)), field);
    }
    return value;
  }

  private static String getLabelText(JComponent field) {
    final JLabel label = (JLabel)field.getClientProperty("labeledBy");
    String text = label.getText();
    text = StringUtil.trimEnd(text, ":");
    return text;
  }

  public static int getPositiveIntFieldValue(JTextField field, boolean check, boolean emptyAllowed, int max) {
    final String text = field.getText().trim();
    if (text.isEmpty()) {
      if (check && !emptyAllowed) {
        throw new InputException(CvsBundle.message("error.message.value.cannot.be.empty", getLabelText(field)), field);
      }
      return -1;
    }
    else {
      try {
        final int intPort = Integer.parseInt(text);
        if (check && (intPort <= 0 || intPort > max)) {
          throw new InputException(CvsBundle.message("error.message.invalid.value", getLabelText(field), text), field);
        }
        return intPort;
      }
      catch (NumberFormatException ex) {
        if (check) {
          throw new InputException(CvsBundle.message("error.message.invalid.value", getLabelText(field), text), field);
        }
        return -1;
      }
    }
  }
}
