/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
