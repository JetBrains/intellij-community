package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

public class GraphCommitCellRenderer extends TypeSafeTableCellRenderer<GraphCommitCell> {
  private static final int MAX_GRAPH_WIDTH = 6;
  private static final int VERTICAL_PADDING = JBUI.scale(7);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final MyComponent myComponent;
  @NotNull private final MyComponent myTemplateComponent;
  @NotNull private final LabelPainter myTooltipPainter;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table,
                                 boolean compact,
                                 boolean showTagNames) {
    myLogData = logData;
    myGraphTable = table;

    myTooltipPainter = new LabelPainter(myLogData, compact, showTagNames);
    myComponent = new MyComponent(logData, painter, table, compact, showTagNames);
    myTemplateComponent = new MyComponent(logData, painter, table, compact, showTagNames);
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

  @Nullable
  public JComponent getTooltip(@NotNull Object value, @NotNull Point point, int row) {
    GraphCommitCell cell = getValue(value);
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      myTooltipPainter.customizePainter(myComponent, refs, myComponent.getBackground(), myComponent.getForeground(),
                                        true/*counterintuitive, but true*/, getColumnWidth());
      if (myTooltipPainter.isLeftAligned()) {
        double distance = point.getX() - myTemplateComponent.getGraphWidth(cell.getPrintElements());
        if (distance > 0 && distance <= getReferencesWidth(row, cell)) {
          return new TooltipReferencesPanel(myLogData, myTooltipPainter, refs);
        }
      }
      else {
        if (getColumnWidth() - point.getX() <= getReferencesWidth(row, cell)) {
          return new TooltipReferencesPanel(myLogData, myTooltipPainter, refs);
        }
      }
    }
    return null;
  }

  public int getPreferredHeight() {
    return myComponent.getPreferredHeight();
  }

  private int getReferencesWidth(int row) {
    return getReferencesWidth(row, getValue(myGraphTable.getModel().getValueAt(row, GraphTableModel.COMMIT_COLUMN)));
  }

  private int getReferencesWidth(int row, @NotNull GraphCommitCell cell) {
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      myTemplateComponent.customize(cell, myGraphTable.isRowSelected(row), myGraphTable.hasFocus(),
                                    row, GraphTableModel.COMMIT_COLUMN);
      return myTemplateComponent.getReferencePainter().getSize().width;
    }

    return 0;
  }

  private int getGraphWidth(int row) {
    GraphCommitCell cell = getValue(myGraphTable.getModel().getValueAt(row, GraphTableModel.COMMIT_COLUMN));
    return myTemplateComponent.getGraphWidth(cell.getPrintElements());
  }

  public int getTooltipXCoordinate(int row) {
    int referencesWidth = getReferencesWidth(row);
    if (referencesWidth != 0) {
      if (myComponent.getReferencePainter().isLeftAligned()) return getGraphWidth(row) + referencesWidth / 2;
      return getColumnWidth() - referencesWidth / 2;
    }
    return getColumnWidth() / 2;
  }

  private int getColumnWidth() {
    return myGraphTable.getColumnModel().getColumn(GraphTableModel.COMMIT_COLUMN).getWidth();
  }

  public void setCompactReferencesView(boolean compact) {
    myComponent.getReferencePainter().setCompact(compact);
    myTemplateComponent.getReferencePainter().setCompact(compact);
  }

  public void setShowTagsNames(boolean showTagNames) {
    myComponent.getReferencePainter().setShowTagNames(showTagNames);
    myTemplateComponent.getReferencePainter().setShowTagNames(showTagNames);
  }

  private static class MyComponent extends SimpleColoredRenderer {
    private static final int FREE_SPACE = 20;
    @NotNull private final VcsLogData myLogData;
    @NotNull private final VcsLogGraphTable myGraphTable;
    @NotNull private final GraphCellPainter myPainter;
    @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
    @NotNull private final LabelPainter myReferencePainter;

    @NotNull protected GraphImage myGraphImage = new GraphImage(UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0);
    @NotNull private Font myFont;
    private int myHeight;

    public MyComponent(@NotNull VcsLogData data,
                       @NotNull GraphCellPainter painter,
                       @NotNull VcsLogGraphTable table,
                       boolean compact,
                       boolean showTags) {
      myLogData = data;
      myPainter = painter;
      myGraphTable = table;

      myReferencePainter = new LabelPainter(myLogData, compact, showTags);

      myIssueLinkRenderer = new IssueLinkRenderer(myLogData.getProject(), this);
      myFont = RectanglePainter.getFont();
      myHeight = calculateHeight();
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      int referencesSize = myReferencePainter.isLeftAligned() ? 0 : myReferencePainter.getSize().width;
      return new Dimension(preferredSize.width + referencesSize, getPreferredHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      int graphImageWidth = myGraphImage.getWidth();

      if (!myReferencePainter.isLeftAligned()) {
        int start = Math.max(graphImageWidth, getWidth() - myReferencePainter.getSize().width);
        myReferencePainter.paint((Graphics2D)g, start, 0, getHeight());
      }
      else {
        myReferencePainter.paint((Graphics2D)g, graphImageWidth, 0, getHeight());
      }

      UIUtil.drawImage(g, myGraphImage.getImage(), 0, 0, null);
    }

    public void customize(@NotNull GraphCommitCell cell, boolean isSelected, boolean hasFocus, int row, int column) {
      clear();
      setPaintFocusBorder(false);
      acquireState(myGraphTable, isSelected, hasFocus, row, column);
      getCellState().updateRenderer(this);
      setBorder(null);

      myGraphImage = getGraphImage(cell.getPrintElements());

      SimpleTextAttributes style = myGraphTable.applyHighlighters(this, row, column, hasFocus, isSelected);

      Collection<VcsRef> refs = cell.getRefsToThisCommit();
      Color baseForeground = ObjectUtils.assertNotNull(myGraphTable.getBaseStyle(row, column, hasFocus, isSelected).getForeground());

      append(""); // appendTextPadding wont work without this
      if (myReferencePainter.isLeftAligned()) {
        myReferencePainter.customizePainter(this, refs, getBackground(), baseForeground, isSelected,
                                            getAvailableWidth(column));

        appendTextPadding(myGraphImage.getWidth() + myReferencePainter.getSize().width + LabelPainter.RIGHT_PADDING);
        appendText(cell, style, isSelected);
      }
      else {
        appendTextPadding(myGraphImage.getWidth());
        appendText(cell, style, isSelected);
        myReferencePainter.customizePainter(this, refs, getBackground(), baseForeground, isSelected,
                                            getAvailableWidth(column));
      }
    }

    private void appendText(@NotNull GraphCommitCell cell, @NotNull SimpleTextAttributes style, boolean isSelected) {
      myIssueLinkRenderer.appendTextWithLinks(StringUtil.replace(cell.getText(), "\t", " ").trim(), style);
      SpeedSearchUtil.applySpeedSearchHighlighting(myGraphTable, this, false, isSelected);
    }

    private int getAvailableWidth(int column) {
      int columnWidth = myGraphTable.getColumnModel().getColumn(column).getWidth();
      int freeSpace = columnWidth - super.getPreferredSize().width;
      if (myReferencePainter.isCompact()) {
        return Math.min(freeSpace, columnWidth / 3);
      }
      else {
        return Math.max(freeSpace, columnWidth / 2);
      }
    }

    private int calculateHeight() {
      return Math.max(myReferencePainter.getSize().height, getFontMetrics(myFont).getHeight() + VERTICAL_PADDING);
    }

    public int getPreferredHeight() {
      Font font = RectanglePainter.getFont();
      if (myFont != font) {
        myFont = font;
        myHeight = calculateHeight();
      }
      return myHeight;
    }

    @NotNull
    private GraphImage getGraphImage(@NotNull Collection<? extends PrintElement> printElements) {
      double maxIndex = getMaxGraphElementIndex(printElements);
      BufferedImage image = UIUtil.createImage((int)(PaintParameters.getNodeWidth(myGraphTable.getRowHeight()) * (maxIndex + 2)),
                                               myGraphTable.getRowHeight(),
                                               BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      myPainter.draw(g2, printElements);

      int width = (int)(maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight()));
      return new GraphImage(image, width);
    }

    private int getGraphWidth(@NotNull Collection<? extends PrintElement> printElements) {
      double maxIndex = getMaxGraphElementIndex(printElements);
      return (int)(maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight()));
    }

    private double getMaxGraphElementIndex(@NotNull Collection<? extends PrintElement> printElements) {
      double maxIndex = 0;
      for (PrintElement printElement : printElements) {
        maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
        if (printElement instanceof EdgePrintElement) {
          maxIndex = Math.max(maxIndex,
                              (printElement.getPositionInCurrentRow() + ((EdgePrintElement)printElement).getPositionInOtherRow()) / 2.0);
        }
      }
      maxIndex++;

      maxIndex = Math.max(maxIndex, Math.min(MAX_GRAPH_WIDTH, myGraphTable.getVisibleGraph().getRecommendedWidth()));
      return maxIndex;
    }

    @NotNull
    public LabelPainter getReferencePainter() {
      return myReferencePainter;
    }
  }

  private static class GraphImage {
    private final int myWidth;
    @NotNull private final Image myImage;

    GraphImage(@NotNull Image image, int width) {
      myImage = image;
      myWidth = width;
    }

    @NotNull
    Image getImage() {
      return myImage;
    }

    /**
     * Returns the "interesting" width of the painted image, i.e. the width which the text in the table should be offset by. <br/>
     * It can be smaller than the width of {@link #getImage() the image}, because we allow the text to cover part of the graph
     * (some diagonal edges, etc.)
     */
    int getWidth() {
      return myWidth;
    }
  }
}
