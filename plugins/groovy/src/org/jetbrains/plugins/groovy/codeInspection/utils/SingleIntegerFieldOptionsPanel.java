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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.text.NumberFormat;

public class SingleIntegerFieldOptionsPanel extends JPanel {

  public SingleIntegerFieldOptionsPanel(String labelString, final BaseInspection owner, @NonNls final String property) {
    super(new GridBagLayout());
    final JLabel label = new JLabel(labelString);
    final NumberFormat formatter = NumberFormat.getIntegerInstance();
    formatter.setParseIntegerOnly(true);
    final JFormattedTextField valueField = new JFormattedTextField(formatter);
    valueField.setValue(getPropertyValue(owner, property));
    valueField.setColumns(2);
    final Document document = valueField.getDocument();
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void changedUpdate(DocumentEvent e) {
        textChanged();
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        textChanged();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        textChanged();
      }

      private void textChanged() {
        setPropertyValue(owner, property, ((Number) valueField.getValue()).intValue());
      }
    });
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.NONE;
    add(label, constraints);
    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.insets.right = 0;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    constraints.fill = GridBagConstraints.NONE;
    add(valueField, constraints);
  }

  private void setPropertyValue(BaseInspection owner, String property, int value) {
    try {
      owner.getClass().getField(property).setInt(owner, value);
    } catch (Exception ignore) {
    }
  }

  private int getPropertyValue(BaseInspection owner, String property) {
    try {
      return owner.getClass().getField(property).getInt(owner);
    } catch (Exception ignore) {
      return 0;
    }
  }
}
