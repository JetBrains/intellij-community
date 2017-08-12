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
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class RootCellRenderer extends JBLabel implements TableCellRenderer {
  @NotNull private final AbstractVcsLogUi myUi;
  @NotNull private Color myColor = UIUtil.getTableBackground();
  @NotNull private Color myBorderColor = UIUtil.getTableBackground();
  private boolean isNarrow = true;

  RootCellRenderer(@NotNull AbstractVcsLogUi ui) {
    super("", CENTER);
    myUi = ui;
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(myColor);

    int width = getWidth();

    if (isNarrow) {
      g.fillRect(0, 0, width - JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), myUi.getTable().getRowHeight());
      g.setColor(myBorderColor);
      g.fillRect(width - JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), 0, JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH),
                 myUi.getTable().getRowHeight());
    }
    else {
      g.fillRect(0, 0, width, myUi.getTable().getRowHeight());
    }

    super.paintComponent(g);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    String text;
    Color color;

    if (value instanceof VirtualFile) {
      VirtualFile root = (VirtualFile)value;
      int readableRow = ScrollingUtil.getReadableRow(table, Math.round(myUi.getTable().getRowHeight() * 0.5f));
      if (row < readableRow) {
        text = "";
      }
      else if (row == 0 || !value.equals(table.getModel().getValueAt(row - 1, column)) || readableRow == row) {
        text = root.getName();
      }
      else {
        text = "";
      }
      color = VcsLogGraphTable.getRootBackgroundColor(root, myUi.getColorManager());
    }
    else {
      text = null;
      color = UIUtil.getTableBackground(isSelected);
    }

    myColor = color;
    Color background = ((VcsLogGraphTable)table).getStyle(row, column, hasFocus, isSelected).getBackground();
    assert background != null;
    myBorderColor = background;
    setForeground(UIUtil.getTableForeground(false));

    if (myUi.isShowRootNames()) {
      setText(text);
      isNarrow = false;
    }
    else {
      setText("");
      isNarrow = true;
    }

    return this;
  }

  @Override
  public void setBackground(Color bg) {
    myBorderColor = bg;
  }
}
