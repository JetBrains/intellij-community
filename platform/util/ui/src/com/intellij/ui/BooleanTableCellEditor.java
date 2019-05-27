// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

public class BooleanTableCellEditor extends DefaultCellEditor {
  private final boolean myStringEditor;

  public BooleanTableCellEditor(boolean isStringEditor) {
    this(isStringEditor, SwingConstants.CENTER);
  }

  public BooleanTableCellEditor(boolean isStringEditor, int horizontalAlignment) {
    super(new JCheckBox());
    myStringEditor = isStringEditor;
    ((JCheckBox) editorComponent).setHorizontalAlignment(horizontalAlignment);
  }

  public BooleanTableCellEditor() {
    this(false);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    return super.getTableCellEditorComponent(table, value, true, row, column);
  }

  @Override
  public Object getCellEditorValue() {
    Object value = super.getCellEditorValue();
    if (myStringEditor && value instanceof Boolean) {
      return value.toString();
    } else {
      return value;
    }
  }
}
