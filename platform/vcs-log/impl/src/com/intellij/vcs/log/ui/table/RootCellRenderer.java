// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColors;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory;
import com.intellij.vcs.log.ui.render.RootCell;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

import static com.intellij.ui.hover.TableHoverListener.getHoveredRow;
import static com.intellij.vcs.log.impl.CommonUiProperties.SHOW_ROOT_NAMES;

@ApiStatus.Internal
public class RootCellRenderer extends SimpleColoredRenderer implements TableCellRenderer, VcsLogCellRenderer {
  private static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + 8;
  private static final int NEW_UI_ROOT_INDICATOR_WIDTH = 10;
  private static final int ROOT_NAME_MAX_WIDTH = 300;

  private final @NotNull VcsLogUiProperties myProperties;
  private final @NotNull VcsLogColorManager myColorManager;
  protected @NotNull Color myColor = UIUtil.getTableBackground();
  protected @NotNull Color myBorderColor = UIUtil.getTableBackground();
  protected boolean isNarrow = true;
  private @NotNull @Nls String myTooltip = "";

  public RootCellRenderer(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColorManager colorManager) {
    myProperties = properties;
    myColorManager = colorManager;
    setTextAlign(SwingConstants.CENTER);
    updateInsets();
  }

  @Override
  protected void paintBackground(Graphics2D g, int x, int width, int height) {
    g.setColor(myColor);

    if (isNarrow) {
      g.fillRect(x, 0, width - JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH), height);
      g.setColor(myBorderColor);
      g.fillRect(x + width - JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH), 0,
                 JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH),
                 height);
    }
    else {
      g.fillRect(x, 0, width, height);
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    clear();

    RootCell cell = (RootCell)value;
    List<FilePath> paths;
    if (cell instanceof RootCell.RealCommit) {
      FilePath path = ((RootCell.RealCommit)cell).getPath();
      paths = ContainerUtil.filter(List.of(path), Objects::nonNull);
    }
    else {
      paths = ((RootCell.NewCommit)cell).getIncludedPaths();
    }

    boolean hovered = row == getHoveredRow(table);

    myBorderColor = Objects.requireNonNull(((VcsLogGraphTable)table).getStyle(row, column, hasFocus, isSelected, hovered).getBackground());
    setForeground(UIUtil.getTableForeground(false, hasFocus));

    if (isShowRootNames()) {
      if (isTextShown(table, value, row, column)) {
        if (paths.isEmpty()) {
          append("");
        }
        else {
          String text;
          if (paths.size() == 1) {
            text = paths.get(0).getName();
          }
          else {
            text = paths.get(0).getName() + "+" + (paths.size() - 1);
          }
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

    if (cell instanceof RootCell.NewCommit) {
      myColor = JBColor.LIGHT_GRAY;
    } else {
      if (paths.isEmpty()) {
        myColor = UIUtil.getTableBackground(isSelected, hasFocus);
      }
      else {
        myColor = myColorManager.getPathColor(paths.get(0), isNarrow
                                                            ? RepositoryColors.DEFAULT_COLOR_SPACE
                                                            : VcsLogColorManagerFactory.ROOT_OPENED_STATE);
      }
    }

    myTooltip = getTooltipText(paths, isNarrow);

    return this;
  }

  @Override
  public @NotNull VcsLogCellController getCellController() {
    return new VcsLogCellController() {
      @Override
      public @Nullable Cursor performMouseClick(int row, @NotNull MouseEvent e) {
        if (myColorManager.hasMultiplePaths() && myProperties.exists(SHOW_ROOT_NAMES)) {
          VcsLogUsageTriggerCollector.triggerClick("root.column");
          myProperties.set(SHOW_ROOT_NAMES, !myProperties.get(SHOW_ROOT_NAMES));
        }
        return null;
      }

      @Override
      public @NotNull MouseMoveResult performMouseMove(int row, @NotNull MouseEvent e) {
        return MouseMoveResult.fromCursor(Cursor.HAND_CURSOR);
      }

      @Override
      public boolean shouldSelectCell(int row, @NotNull MouseEvent e) {
        return false;
      }
    };
  }

  public void updateInsets() {
    setBorderInsets(isShowRootNames() ? getRootNameInsets() : JBUI.emptyInsets());
  }

  protected Insets getRootNameInsets() {
    return JBUI.emptyInsets();
  }

  private boolean isShowRootNames() {
    return myProperties.exists(SHOW_ROOT_NAMES) && myProperties.get(SHOW_ROOT_NAMES);
  }

  private static boolean isTextShown(JTable table, Object value, int row, int column) {
    if (value instanceof RootCell.NewCommit) return true;
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

  @Override
  public String getToolTipText(MouseEvent event) {
    return myTooltip;
  }

  public int getColumnWidth() {
    if (!myColorManager.hasMultiplePaths()) {
      return 0;
    }
    if (!isShowRootNames()) {
      return JBUIScale.scale(ExperimentalUI.isNewUI() ? NEW_UI_ROOT_INDICATOR_WIDTH : ROOT_INDICATOR_WIDTH);
    }

    Font tableFont = VcsLogGraphTable.getTableFont();
    int textWidth = 0;
    for (FilePath file : myColorManager.getPaths()) {
      textWidth = Math.max(getFontMetrics(tableFont).stringWidth(file.getName() + "  "), textWidth);
    }
    Insets componentInsets = getRootNameInsets();
    int insets = componentInsets.left + componentInsets.right;
    return Math.min(textWidth + insets, JBUIScale.scale(ROOT_NAME_MAX_WIDTH));
  }

  private @NotNull @Nls String getTooltipText(@Nullable List<FilePath> paths, boolean isNarrow) {
    String clickMessage = !isNarrow
                          ? VcsLogBundle.message("vcs.log.click.to.collapse.paths.column.tooltip")
                          : VcsLogBundle.message("vcs.log.click.to.expand.paths.column.tooltip");
    if (paths == null || paths.isEmpty()) return clickMessage;
    HtmlBuilder htmlBuilder = new HtmlBuilder();
    for (FilePath path : paths) {
      htmlBuilder.append(HtmlChunk.text(myColorManager.getLongName(path)).bold()).br();
    }
    htmlBuilder.append(clickMessage);
    return htmlBuilder.wrapWith(HtmlChunk.html()).toString();
  }
}
