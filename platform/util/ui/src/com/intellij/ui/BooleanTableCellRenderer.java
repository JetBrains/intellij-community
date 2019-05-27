// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.Couple;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class BooleanTableCellRenderer extends JCheckBox implements TableCellRenderer {
  private final JPanel myPanel = new JPanel();

  public BooleanTableCellRenderer() {
    this(CENTER);
  }

  public BooleanTableCellRenderer(final int horizontalAlignment) {
    super();
    setHorizontalAlignment(horizontalAlignment);
    setVerticalAlignment(CENTER);
    setBorder(null);
    setOpaque(true);
    myPanel.setOpaque(true);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSel, boolean hasFocus, int row, int column) {
    Couple<Color> colors = UIUtil.getCellColors(table, isSel, row, column);

    if (value == null) {
      myPanel.setBackground(colors.getSecond());
      return myPanel;
    }

    setForeground(colors.getFirst());
    setBackground(colors.getSecond());

    if (value instanceof String) {
      setSelected(Boolean.parseBoolean((String)value));
    }
    else {
      setSelected(((Boolean)value).booleanValue());
    }
    setEnabled(table.isCellEditable(row, column));
    return this;
  }
}
