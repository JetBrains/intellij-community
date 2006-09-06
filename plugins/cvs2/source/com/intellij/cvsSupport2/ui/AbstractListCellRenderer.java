package com.intellij.cvsSupport2.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public abstract class AbstractListCellRenderer extends DefaultListCellRenderer {
  protected abstract String getPresentableString(Object value);
  @Nullable
  protected Icon getPresentableIcon(Object value){
    return null;
  }

  public Component getListCellRendererComponent(
      JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {

    Component result = super.getListCellRendererComponent(list, getPresentableString(value), index, isSelected, cellHasFocus);

    Icon icon = getPresentableIcon(value);
    if (icon != null)
      setIcon(icon);

    return result;
  }

}
