/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

public class FormUtils {

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
