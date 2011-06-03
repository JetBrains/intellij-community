/*
 * Copyright 2007-2008 Dave Griffith
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
package com.intellij.util.ui;

import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.text.ParseException;

public class RegExInputVerifier extends InputVerifier {

  public boolean verify(JComponent input) {
    return true;
  }

  public boolean shouldYieldFocus(JComponent input) {
    if (input instanceof JFormattedTextField) {
      final JFormattedTextField ftf = (JFormattedTextField) input;
      final JFormattedTextField.AbstractFormatter formatter =
          ftf.getFormatter();
      if (formatter != null) {
        try {
          formatter.stringToValue(ftf.getText());
        } catch (final ParseException e) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(e.getMessage(),
                  "Malformed Naming Pattern");
            }
          });
        }
      }
    }
    return true;
  }
}