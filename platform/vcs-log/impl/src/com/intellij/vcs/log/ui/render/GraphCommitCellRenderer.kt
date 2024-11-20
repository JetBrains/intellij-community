// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent;
import com.intellij.ui.*;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogTextFilter;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import com.intellij.vcs.log.ui.table.GraphCommitCellController;
import com.intellij.vcs.log.ui.table.VcsLogCellController;
import com.intellij.vcs.log.ui.table.VcsLogCellRenderer;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.Commit;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.ui.table.links.VcsLinksRenderer;
import com.intellij.vcs.log.visible.filters.VcsLogTextFilterWithMatches;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@ApiStatus.Internal
public class GraphCommitCellRenderer extends TypeSafeTableCellRenderer<GraphCommitCell>
  implements VcsLogCellRenderer {
  private static final int MAX_GRAPH_WIDTH = 6;

  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogGraphTable myGraphTable;

  private final @NotNull MyComponent myComponent;
  private final @NotNull MyComponent myTemplateComponent;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table) {
    myLogData = logData;
    myGraphTable = table;

    LabelIconCache iconCache = new LabelIconCache();
    myComponent = new MyComponent(logData, painter, table, iconCache);
    myTemplateComponent = new MyComponent(logData, painter, table, iconCache);
  }

  @Override
  protected SimpleColoredComponent getTableCellRendererComponentImpl(@NotNull JTable table,
                                                                     @NotNull GraphCommitCell value,
                                                                     boolean isSelected,
                                                                     boolean hasFocus,
                                                                     int row,
                                                                     int column) {
    myComponent.customize(value, isSelected, hasFocus, row, column);
    return myComponent;
  }

  private @Nullable JComponent getTooltip(@NotNull Object value, @NotNull Point point, int row) {
    GraphCommitCell cell = getValue(value);
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    Collection<VcsBookmarkRef> bookmarks = cell.getBookmarksToThisCommit();
    if (refs.isEmpty() && bookmarks.isEmpty()) return null;

    prepareTemplateComponent(row, cell);
    if (myTemplateComponent.getReferencePainter().isLeftAligned()) {
      double distance = point.getX() - myTemplateComponent.getGraphWidth();
      if (distance > 0 && distance <= myTemplateComponent.getReferencesWidth()) {
        return new TooltipReferencesPanel(myLogData, refs, bookmarks);
      }
    }
    else {
      if (getColumnWidth() - point.getX() <= myTemplateComponent.getReferencesWidth()) {
        return new TooltipReferencesPanel(myLogData, refs, bookmarks);
      }
    }
    return null;
  }

  private int getTooltipXCoordinate(int row) {
    GraphCommitCell cell = getValue(myGraphTable.getModel().getValueAt(row, Commit.INSTANCE));
    if (cell.getRefsToThisCommit().isEmpty() && cell.getBookmarksToThisCommit().isEmpty()) return getColumnWidth() / 2;

    prepareTemplateComponent(row, cell);
    int referencesWidth = myTemplateComponent.getReferencesWidth();
    if (myTemplateComponent.getReferencePainter().isLeftAligned()) {
      return myTemplateComponent.getGraphWidth() + referencesWidth / 2;
    }
    return getColumnWidth() - referencesWidth / 2;
  }

  private void prepareTemplateComponent(int row, @NotNull GraphCommitCell cell) {
    myTemplateComponent.customize(cell, myGraphTable.isRowSelected(row), myGraphTable.hasFocus(),
                                  row, VcsLogColumnManager.getInstance().getModelIndex(Commit.INSTANCE));
  }

  private int getColumnWidth() {
    return myGraphTable.getCommitColumn().getWidth();
  }

  public int getPreferredHeight() {
    return myComponent.getPreferredHeight();
  }

  public void setCompactReferencesView(boolean compact) {
    myComponent.getReferencePainter().setCompact(compact);
    myTemplateComponent.getReferencePainter().setCompact(compact);
  }

  public void setShowTagsNames(boolean showTagNames) {
    myComponent.getReferencePainter().setShowTagNames(showTagNames);
    myTemplateComponent.getReferencePainter().setShowTagNames(showTagNames);
  }

  public void setLeftAligned(boolean leftAligned) {
    myComponent.getReferencePainter().setLeftAligned(leftAligned);
    myTemplateComponent.getReferencePainter().setLeftAligned(leftAligned);
  }

  @Override
  public @NotNull VcsLogCellController getCellController() {
    return new GraphCommitCellController(myLogData, myGraphTable, myComponent.myPainter) {

      @Override
      protected int getTooltipXCoordinate(int row) {
        return GraphCommitCellRenderer.this.getTooltipXCoordinate(row);
      }

      @Override
      protected @Nullable JComponent getTooltip(@NotNull Object value, @NotNull Point point, int row) {
        return GraphCommitCellRenderer.this.getTooltip(value, point, row);
      }
    };
  }

  public static Font getLabelFont() {
    return StartupUiUtil.getLabelFont();
  }

  private static class MyComponent extends SimpleColoredRenderer {
    private static final int DISPLAYED_MESSAGE_PART = 80;
    private final @NotNull VcsLogGraphTable myGraphTable;
    private final @NotNull GraphCellPainter myPainter;
    private final @NotNull IssueLinkRenderer myIssueLinkRenderer;
    private final @NotNull VcsLinksRenderer myVcsLinksRenderer;
    private final @NotNull VcsLogLabelPainter myReferencePainter;

    private @NotNull Collection<? extends PrintElement> myPrintElements = Collections.emptyList();
    private @NotNull Font myFont;
    private int myHeight;
    private int myGraphWidth = 0;
    private AffineTransform myAffineTransform;

    MyComponent(@NotNull VcsLogData data,
                @NotNull GraphCellPainter painter,
                @NotNull VcsLogGraphTable table,
                @NotNull LabelIconCache iconCache) {
      myPainter = painter;
      myGraphTable = table;

      myReferencePainter = new VcsLogLabelPainter(data, table, iconCache);
      myVcsLinksRenderer = new VcsLinksRenderer(data.getProject(), this);
      myIssueLinkRenderer = new IssueLinkRenderer(data.getProject(), this);
      setCellState(new VcsLogTableCellState());

      myFont = getLabelFont();
      GraphicsConfiguration configuration = myGraphTable.getGraphicsConfiguration();
      myAffineTransform = configuration != null ? configuration.getDefaultTransform() : null;
      myHeight = calculateHeight();
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      int referencesSize = myReferencePainter.isLeftAligned() ? 0 : myReferencePainter.getSize().width;
      return new Dimension(preferredSize.width + referencesSize, getPreferredHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      Graphics2D g2d = (Graphics2D)g;
      if (!myReferencePainter.isLeftAligned()) {
        int start = Math.max(myGraphWidth, getWidth() - myReferencePainter.getSize().width);
        myReferencePainter.paint(g2d, start, 0, getHeight());
      }
      else {
        myReferencePainter.paint(g2d, myGraphWidth, 0, getHeight());
      }
      myPainter.paint(g2d, myPrintElements);
    }

    public void customize(@NotNull GraphCommitCell cell, boolean isSelected, boolean hasFocus, int row, int column) {
      clear();
      setPaintFocusBorder(false);
      acquireState(myGraphTable, isSelected, hasFocus, row, column);
      getCellState().updateRenderer(this);

      myPrintElements = cell.getPrintElements();
      myGraphWidth = getGraphWidth(myGraphTable, myPrintElements);

      SimpleTextAttributes style = myGraphTable.applyHighlighters(this, row, column, hasFocus, isSelected);

      Collection<VcsRef> refs = cell.getRefsToThisCommit();
      Collection<VcsBookmarkRef> bookmarks = cell.getBookmarksToThisCommit();
      Color labelForeground;
      if (ExperimentalUI.isNewUI()) {
        labelForeground = JBColor.namedColor("VersionControl.Log.Commit.Reference.foreground", CurrentBranchComponent.TEXT_COLOR);
      }
      else {
        labelForeground = isSelected
                          ? Objects.requireNonNull(myGraphTable.getBaseStyle(row, column, hasFocus, isSelected).getForeground())
                          : CurrentBranchComponent.TEXT_COLOR;
      }

      append(""); // appendTextPadding wont work without this
      boolean renderLinks = !cell.isLoading();
      if (myReferencePainter.isLeftAligned()) {
        myReferencePainter.customizePainter(refs, bookmarks, getBackground(), labelForeground, isSelected,
                                            getAvailableWidth(column, myGraphWidth));

        int referencesWidth = myReferencePainter.getSize().width;
        if (referencesWidth > 0) referencesWidth += LabelPainter.RIGHT_PADDING.get();
        appendTextPadding(myGraphWidth + referencesWidth);
        appendText(cell, style, isSelected, renderLinks);
      }
      else {
        appendTextPadding(myGraphWidth);
        appendText(cell, style, isSelected, renderLinks);
        myReferencePainter.customizePainter(refs, bookmarks, getBackground(), labelForeground, isSelected,
                                            getAvailableWidth(column, myGraphWidth));
      }
    }

    private void appendText(@NotNull GraphCommitCell cell, @NotNull SimpleTextAttributes style, boolean isSelected, boolean renderLinks) {
      String cellText = StringUtil.replace(cell.getText(), "\t", " ").trim();
      CommitId commitId = cell.getCommitId();

      if (renderLinks) {
        if (VcsLinksRenderer.isEnabled()) {
          myVcsLinksRenderer.appendTextWithLinks(cellText, style, commitId);
        }
        else {
          myIssueLinkRenderer.appendTextWithLinks(cellText, style);
        }
      }
      else {
        append(cellText, style);
      }

      SpeedSearchUtil.applySpeedSearchHighlighting(myGraphTable, this, false, isSelected);
      if (Registry.is("vcs.log.filter.text.highlight.matches")) {
        VcsLogTextFilter textFilter = myGraphTable.getModel().getVisiblePack().getFilters().get(VcsLogFilterCollection.TEXT_FILTER);
        if (textFilter instanceof VcsLogTextFilterWithMatches textFilterWithMatches) {
          String text = getCharSequence(false).toString();
          SpeedSearchUtil.applySpeedSearchHighlighting(this, textFilterWithMatches.matchingRanges(text), isSelected);
        }
      }
    }

    private int getAvailableWidth(int column, int graphWidth) {
      int textAndLabelsWidth = myGraphTable.getColumnModel().getColumn(column).getWidth() - graphWidth;
      int freeSpace = textAndLabelsWidth - super.getPreferredSize().width;
      int allowedSpace;
      if (myReferencePainter.isCompact()) {
        allowedSpace = Math.min(freeSpace, textAndLabelsWidth / 3);
      }
      else {
        allowedSpace = Math.max(freeSpace, Math.max(textAndLabelsWidth / 2, textAndLabelsWidth - JBUIScale.scale(DISPLAYED_MESSAGE_PART)));
      }
      return Math.max(0, allowedSpace);
    }

    private int calculateHeight() {
      int rowContentHeight = calculateRowContentHeight();
      return ExperimentalUI.isNewUI() ?
             Math.max(rowContentHeight, JBUI.CurrentTheme.VersionControl.Log.rowHeight()) :
             rowContentHeight;
    }

    private int calculateRowContentHeight() {
      return Math.max(myReferencePainter.getSize().height,
                      getFontMetrics(myFont).getHeight() + JBUI.scale(JBUI.CurrentTheme.VersionControl.Log.verticalPadding()));
    }

    public int getPreferredHeight() {
      Font font = getLabelFont();
      GraphicsConfiguration configuration = myGraphTable.getGraphicsConfiguration();
      if (myFont != font || (configuration != null && !Objects.equals(myAffineTransform, configuration.getDefaultTransform()))) {
        myFont = font;
        myAffineTransform = configuration != null ? configuration.getDefaultTransform() : null;
        myHeight = calculateHeight();
      }
      return myHeight;
    }

    public @NotNull VcsLogLabelPainter getReferencePainter() {
      return myReferencePainter;
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
      return myGraphTable.getFontMetrics(font);
    }

    private int getGraphWidth() {
      return myGraphWidth;
    }

    private int getReferencesWidth() {
      return myReferencePainter.getSize().width;
    }

    private static int getGraphWidth(@NotNull VcsLogGraphTable table, @NotNull Collection<? extends PrintElement> printElements) {
      if (printElements.isEmpty()) return 0;

      double maxIndex = 0;
      for (PrintElement printElement : printElements) {
        maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
        if (printElement instanceof EdgePrintElement) {
          maxIndex = Math.max(maxIndex,
                              (printElement.getPositionInCurrentRow() + ((EdgePrintElement)printElement).getPositionInOtherRow()) / 2.0);
        }
      }
      maxIndex++;
      maxIndex = Math.max(maxIndex, Math.min(MAX_GRAPH_WIDTH, table.getVisibleGraph().getRecommendedWidth()));

      return (int)(maxIndex * PaintParameters.getElementWidth(table.getRowHeight()));
    }
  }

  public static class VcsLogTableCellState extends TableCellState {
    @Override
    protected @Nullable Border getBorder(boolean isSelected, boolean hasFocus) {
      return null;
    }

    @Override
    protected @NotNull Color getSelectionForeground(JTable table, boolean isSelected) {
      if (!isSelected) return super.getSelectionForeground(table, isSelected);
      return VcsLogGraphTable.getSelectionForeground(RenderingUtil.isFocused(table));
    }
  }
}
