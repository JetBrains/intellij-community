// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;


public class LabelWithTooltip extends JLabel implements TableCellRenderer{
  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setText((String) value);
    setToolTipText((String) value);
    setOpaque(isSelected);
    if (isSelected){
      setBackground(table.getSelectionBackground());
      setForeground(table.getSelectionForeground());
    } else {
      setBackground(table.getBackground());
      setForeground(table.getForeground());
    }
    return this;
  }
}
