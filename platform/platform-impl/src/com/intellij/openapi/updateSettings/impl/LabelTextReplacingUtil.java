// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public final class LabelTextReplacingUtil {

  /**
   * replace
   *   $PRODUCT$ -> ApplicationNamesInfo.getInstance().getProductName()
   *   $FULLNAME$ -> ApplicationNamesInfo.getInstance().getFullProductName()
   * in text of component's labels
   */
  public static void replaceText(JComponent component) {
    for (Component child : UIUtil.uiTraverser(component)) {
      if (child instanceof JLabel) {
        final JLabel label = (JLabel)child;
        String oldText = label.getText();
        if (oldText != null) {
          label.setText(doReplace(oldText));
        }
      }
      else if (child instanceof AbstractButton) {
        AbstractButton button = (AbstractButton)child;
        String oldText = button.getText();
        if (oldText != null) {
          button.setText(doReplace(oldText));
        }
      }
    }
  }

  private static String doReplace(String oldText) {
    String newText = StringUtil.replace(oldText, "$PRODUCT$", ApplicationNamesInfo.getInstance().getProductName());
    newText = StringUtil.replace(newText, "$FULLNAME$", ApplicationNamesInfo.getInstance().getFullProductName());
    return newText;
  }
}
