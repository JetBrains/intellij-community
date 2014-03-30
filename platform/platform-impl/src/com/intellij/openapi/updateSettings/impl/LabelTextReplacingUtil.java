/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.IJSwingUtilities;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * @author nik
 */
public class LabelTextReplacingUtil {

  /**
   * replace
   *   $PRODUCT$ -> ApplicationNamesInfo.getInstance().getProductName()
   *   $FULLNAME$ -> ApplicationNamesInfo.getInstance().getFullProductName()
   * in text of component's labels
   */
  public static void replaceText(JComponent component) {
    final Iterator<Component> children = IJSwingUtilities.getChildren(component);
    while (children.hasNext()) {
      Component child = children.next();
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
