package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.frame.XExecutionStack;

import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class ThreadComboBoxRenderer extends BasicComboBoxRenderer {
  public Component getListCellRendererComponent(final JList list,
                                                final Object value,
                                                final int index,
                                                final boolean isSelected,
                                                final boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (value != null) {
      XExecutionStack executionStack = (XExecutionStack)value;
      setText(executionStack.getDisplayName());
      setIcon(executionStack.getIcon());
    }
    return this;
  }
}
