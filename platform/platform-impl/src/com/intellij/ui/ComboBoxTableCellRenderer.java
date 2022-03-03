// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Collections;
import java.util.EnumSet;

import static com.intellij.util.ObjectUtils.objectInfo;

public final class ComboBoxTableCellRenderer extends JPanel implements TableCellRenderer {
  public static final TableCellRenderer INSTANCE = new ComboBoxTableCellRenderer();

  /**
   * DefaultTableCellRenderer, that displays JComboBox on selected value.
   */
  public static final TableCellRenderer COMBO_WHEN_SELECTED_RENDERER = new DefaultTableCellRenderer() {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (isSelected) {
        value = new ListWithSelection<>(Collections.singletonList(value));
        return INSTANCE.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  };

  private static final Logger LOG = Logger.getInstance(ComboBoxTableCellRenderer.class);

  private final JComboBox myCombo = new ComboBox();

  private ComboBoxTableCellRenderer() {
    super(new GridBagLayout());
    add(myCombo,
        new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));
  }

  @Override
  @SuppressWarnings("unchecked")
  public JComponent getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value instanceof ListWithSelection) {
      ListWithSelection tags = (ListWithSelection)value;
      if (tags.getSelection() == null) tags.selectFirst();
      updateCombobox(tags, tags.getSelection());
    }
    else if (value instanceof Enum) {
      Enum selectedValue = (Enum)value;
      updateCombobox(EnumSet.allOf(selectedValue.getDeclaringClass()), selectedValue);
    }
    else {
      if (value != null) {
        LOG.error("value " + objectInfo(value) + ", at " + row + ":" + column + ", in " + table.getModel());
      }
      myCombo.removeAllItems();
      myCombo.setSelectedIndex(-1);
    }

    myCombo.setEnabled(table.isCellEditable(row, column));

    return this;
  }

  @SuppressWarnings("unchecked")
  private void updateCombobox(@NotNull Iterable<?> options, @NotNull Object selectedOption) {
    myCombo.removeAllItems();
    for (Object option : options) {
      myCombo.addItem(option);
    }
    myCombo.setSelectedItem(selectedOption);
  }
}
