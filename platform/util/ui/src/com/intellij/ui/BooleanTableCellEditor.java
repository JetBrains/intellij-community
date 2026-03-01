// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.Component;

public class BooleanTableCellEditor extends DefaultCellEditor {

  public BooleanTableCellEditor() {
    super(new JCheckBox());
    ((JCheckBox) editorComponent).setHorizontalAlignment(SwingConstants.CENTER);
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    return super.getTableCellEditorComponent(table, value, true, row, column);
  }
}
