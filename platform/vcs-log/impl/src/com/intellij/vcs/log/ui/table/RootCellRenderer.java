// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Objects;

import static com.intellij.vcs.log.impl.CommonUiProperties.SHOW_ROOT_NAMES;

class RootCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private Color myColor = UIUtil.getTableBackground();
  @NotNull private Color myBorderColor = UIUtil.getTableBackground();
  private boolean isNarrow = true;

  RootCellRenderer(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColorManager colorManager) {
    myProperties = properties;
    myColorManager = colorManager;
    setTextAlign(SwingConstants.CENTER);
  }

  @Override
  protected void paintBackground(Graphics2D g, int x, int width, int height) {
    g.setColor(myColor);

    if (isNarrow) {
      g.fillRect(x, 0, width - JBUIScale.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), height);
      g.setColor(myBorderColor);
      g.fillRect(x + width - JBUIScale.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH), 0,
                 JBUIScale.scale(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH),
                 height);
    }
    else {
      g.fillRect(x, 0, width, height);
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    clear();

    VirtualFile root = (VirtualFile)value;

    myColor = root == null ? UIUtil.getTableBackground(isSelected, hasFocus) :
              VcsLogGraphTable.getRootBackgroundColor(root, myColorManager);
    myBorderColor = ObjectUtils.assertNotNull(((VcsLogGraphTable)table).getStyle(row, column, hasFocus, isSelected).getBackground());
    setForeground(UIUtil.getTableForeground(false, hasFocus));

    if (myProperties.exists(SHOW_ROOT_NAMES) && myProperties.get(SHOW_ROOT_NAMES)) {
      if (isTextShown(table, value, row, column)) {
        append(root == null ? "" : root.getName());
      }
      isNarrow = false;
    }
    else {
      append("");
      isNarrow = true;
    }

    return this;
  }

  private static boolean isTextShown(JTable table, Object value, int row, int column) {
    int readableRow = ScrollingUtil.getReadableRow(table, Math.round(table.getRowHeight() * 0.5f));
    if (row < readableRow) {
      return false;
    }
    return row == 0 || readableRow == row || !Objects.equals(value, table.getModel().getValueAt(row - 1, column));
  }

  @Override
  public void setBackground(Color bg) {
    myBorderColor = bg;
  }
}
