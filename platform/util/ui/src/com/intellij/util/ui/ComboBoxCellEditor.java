// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
