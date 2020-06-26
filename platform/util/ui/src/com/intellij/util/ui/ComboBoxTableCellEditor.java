// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.ListWithSelection;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @deprecated Please use {@link com.intellij.util.ui.table.ComboBoxTableCellEditor}
 */
@Deprecated
public final class ComboBoxTableCellEditor extends AbstractTableCellEditor {
  public static final ComboBoxTableCellEditor INSTANCE = new ComboBoxTableCellEditor();

  private final JPanel myPanel = new JPanel(new GridBagLayout());
  private final JComboBox myComboBox = new JComboBox();

  private ComboBoxTableCellEditor() {
    myComboBox.setRenderer(new BasicComboBoxRenderer());
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopCellEditing();
      }
    });
    myPanel.add(myComboBox,
                new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0,
                                       0));
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final ListWithSelection options = (ListWithSelection)value;
    if (options.getSelection() == null) {
      options.selectFirst();
    }
    myComboBox.removeAllItems();
    for (Object option : options) {
      //noinspection unchecked
      myComboBox.addItem(option);
    }

    myComboBox.setSelectedItem(options.getSelection());

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myComboBox.getSelectedItem();
  }

  public Dimension getPreferedSize() {
    return myComboBox.getPreferredSize();
  }

}
