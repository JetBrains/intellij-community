package com.intellij.grazie.ide.ui;

import com.intellij.util.ui.JBEmptyBorder;

import javax.swing.*;
import java.awt.*;

public class PaddedListCellRenderer extends DefaultListCellRenderer {
  final JBEmptyBorder border = new JBEmptyBorder(0, 10, 0, 10);

  @Override
  public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    setBorder(border);
    return this;
  }
}
