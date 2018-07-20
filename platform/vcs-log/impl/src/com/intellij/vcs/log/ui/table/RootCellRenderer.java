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
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

import static com.intellij.vcs.log.impl.CommonUiProperties.SHOW_ROOT_NAMES;

class RootCellRenderer extends JBLabel implements TableCellRenderer {
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private Color myColor = UIUtil.getTableBackground();
  @NotNull private Color myBorderColor = UIUtil.getTableBackground();
  private boolean isNarrow = true;
  private int myRowHeight;

  RootCellRenderer(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColorManager colorManager) {
    super("", CENTER);
    myProperties = properties;
    myColorManager = colorManager;
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(myColor);

    int width = getWidth();

    if (isNarrow) {
      g.fillRect(0, 0, width - JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), myRowHeight);
      g.setColor(myBorderColor);
      g.fillRect(width - JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), 0,
                 JBUI.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH),
                 myRowHeight);
    }
    else {
      g.fillRect(0, 0, width, myRowHeight);
    }

    super.paintComponent(g);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    String text;
    Color color;

    if (value instanceof VirtualFile) {
      VirtualFile root = (VirtualFile)value;
      int readableRow = ScrollingUtil.getReadableRow(table, Math.round(table.getRowHeight() * 0.5f));
      if (row < readableRow) {
        text = "";
      }
      else if (row == 0 || !value.equals(table.getModel().getValueAt(row - 1, column)) || readableRow == row) {
        text = root.getName();
      }
      else {
        text = "";
      }
      color = VcsLogGraphTable.getRootBackgroundColor(root, myColorManager);
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

    if (myProperties.exists(SHOW_ROOT_NAMES) && myProperties.get(SHOW_ROOT_NAMES)) {
      setText(text);
      isNarrow = false;
    }
    else {
      setText("");
      isNarrow = true;
    }

    myRowHeight = table.getRowHeight();

    return this;
  }

  @Override
  public void setBackground(Color bg) {
    myBorderColor = bg;
  }
}
