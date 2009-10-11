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
package com.intellij.util.ui;

import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author peter
 */
public abstract class ComboBoxCellEditor extends DefaultCellEditor {
  public ComboBoxCellEditor() {
    super(new JComboBox());
    setClickCountToStart(2);
  }

  protected abstract List<String> getComboBoxItems();

  protected boolean isComboboxEditable() {
    return false;
  }

  @Override
  public boolean stopCellEditing() {
    final JComboBox comboBox = (JComboBox)editorComponent;
    comboBox.removeActionListener(delegate);
    final boolean result = super.stopCellEditing();
    comboBox.addActionListener(delegate);
    return result;
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    String currentValue = (String)value;
    final JComboBox component = (JComboBox)super.getTableCellEditorComponent(table, value, isSelected, row, column);
    component.removeActionListener(delegate);
    component.setBorder(null);
    component.removeAllItems();
    final List<String> items = getComboBoxItems();
    int selected = -1;
    for (int i = 0; i < items.size(); i++) {
      final String item = items.get(i);
      component.addItem(item);
      if (Comparing.equal(item, currentValue)) {
        selected = i;
      }
    }
    if (selected == -1) {
      component.setEditable(true);
      component.setSelectedItem(currentValue);
      component.setEditable(false);
    } else {
      component.setSelectedIndex(selected);
    }
    component.setEditable(isComboboxEditable());
    component.addActionListener(delegate);
    return component;
  }
}
