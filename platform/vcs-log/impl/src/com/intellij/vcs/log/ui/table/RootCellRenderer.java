// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Objects;

import static com.intellij.ui.hover.TableHoverListener.getHoveredRow;
import static com.intellij.vcs.log.impl.CommonUiProperties.SHOW_ROOT_NAMES;

public class RootCellRenderer extends SimpleColoredRenderer implements TableCellRenderer, VcsLogCellRenderer {
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private Color myColor = UIUtil.getTableBackground();
  @NotNull private Color myBorderColor = UIUtil.getTableBackground();
  private boolean isNarrow = true;

  public RootCellRenderer(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColorManager colorManager) {
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

    FilePath path = (FilePath)value;

    myColor = path == null ? UIUtil.getTableBackground(isSelected, hasFocus) :
              VcsLogGraphTable.getPathBackgroundColor(path, myColorManager);
    myBorderColor = Objects.requireNonNull(((VcsLogGraphTable)table).getStyle(row, column, hasFocus, isSelected,
                                                                              row == getHoveredRow(table)).getBackground());
    setForeground(UIUtil.getTableForeground(false, hasFocus));

    if (myProperties.exists(SHOW_ROOT_NAMES) && myProperties.get(SHOW_ROOT_NAMES)) {
      if (isTextShown(table, value, row, column)) {
        if (path == null) {
          append("");
        }
        else {
          String text = path.getName();
          int availableWidth = ((VcsLogGraphTable)table).getRootColumn().getWidth() -
                               VcsLogUiUtil.getHorizontalTextPadding(this);
          text = VcsLogUiUtil.shortenTextToFit(text, getFontMetrics(VcsLogGraphTable.getTableFont()),
                                               availableWidth, 0, StringUtil.ELLIPSIS);
          append(text);
        }
      }
      isNarrow = false;
    }
    else {
      append("");
      isNarrow = true;
    }

    return this;
  }

  @NotNull
  @Override
  public VcsLogCellController getCellController() {
    return new VcsLogCellController() {
      @Nullable
      @Override
      public Cursor performMouseClick(int row, @NotNull MouseEvent e) {
        if (myColorManager.hasMultiplePaths() && myProperties.exists(SHOW_ROOT_NAMES)) {
          VcsLogUsageTriggerCollector.triggerClick("root.column");
          myProperties.set(SHOW_ROOT_NAMES, !myProperties.get(SHOW_ROOT_NAMES));
        }
        return null;
      }

      @NotNull
      @Override
      public Cursor performMouseMove(int row, @NotNull MouseEvent e) {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
    };
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
