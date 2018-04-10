/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Collections;
import java.util.EnumSet;

public class ComboBoxTableCellRenderer extends JPanel implements TableCellRenderer {
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

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.ComboBoxTableCellRenderer");

  private final JComboBox myCombo = new ComboBox();

  private ComboBoxTableCellRenderer() {
    super(new GridBagLayout());
    add(myCombo,
        new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
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
        LOG.error("value " + LogUtil.objectAndClass(value) + ", at " + row + ":" + column + ", in " + table.getModel());
      }
      myCombo.removeAllItems();
      myCombo.setSelectedIndex(-1);
    }

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
