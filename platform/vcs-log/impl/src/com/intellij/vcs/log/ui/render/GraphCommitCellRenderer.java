package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableCell;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

public class GraphCommitCellRenderer extends TypeSafeTableCellRenderer<GraphCommitCell> {
  private static final Logger LOG = Logger.getInstance(GraphCommitCellRenderer.class);
  private static final int MAX_GRAPH_WIDTH = 6;

  private static final int VERTICAL_PADDING = JBUI.scale(7);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @Nullable private final FadeOutPainter myFadeOutPainter = isRedesignedLabels() ? new FadeOutPainter() : null;
  @Nullable private final ReferencePainter myTooltipPainter = isRedesignedLabels() ? new LabelPainter() : null;

  @NotNull private final MyComponent myComponent;

  private boolean myExpanded;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table) {
    myLogData = logData;
    myGraphTable = table;

    myComponent = new MyComponent(logData, painter, table) {
      @Override
      public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (myFadeOutPainter != null) {
          if (!myExpanded) {
            int start = Math.max((myGraphImage != null) ? myGraphImage.getWidth() : 0, getWidth() - myFadeOutPainter.getWidth());
            myFadeOutPainter.paint((Graphics2D)g, start, 0, getHeight());
          }
        }
      }
    };
  }

  @Override
  protected Component getTableCellRendererComponentImpl(@NotNull JTable table,
                                                        @NotNull GraphCommitCell value,
                                                        boolean isSelected,
                                                        boolean hasFocus,
                                                        int row,
                                                        int column) {
    myComponent.customize(value, isSelected, hasFocus, row, column);

    myExpanded = myGraphTable.getExpandableItemsHandler().getExpandedItems().contains(new TableCell(row, column));
    if (myFadeOutPainter != null) {
      myFadeOutPainter.customize(value.getRefsToThisCommit(), row, column, table, JBColor.black /*any color fits here*/);
    }
    return myComponent;
  }

  public static boolean isRedesignedLabels() {
    return Registry.is("vcs.log.labels.redesign");
  }

  @Nullable
  public JComponent getTooltip(@NotNull Object value, @NotNull Point point, int width) {
    if (myTooltipPainter == null) return null;

    GraphCommitCell cell = getValue(value);
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      customizeRefsPainter(myTooltipPainter, refs, myComponent.getForeground(), myLogData, myComponent);
      if (myTooltipPainter.getSize().getWidth() - LabelPainter.GRADIENT_WIDTH >= width - point.getX()) {
        return new TooltipReferencesPanel(myLogData, myTooltipPainter, refs);
      }
    }
    return null;
  }

  private static void customizeRefsPainter(@NotNull ReferencePainter painter,
                                           @NotNull Collection<VcsRef> refs,
                                           @NotNull Color foreground,
                                           @NotNull VcsLogData logData,
                                           @NotNull JComponent component) {
    if (!refs.isEmpty()) {
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(refs)).getRoot();
      painter.customizePainter(component, refs, logData.getLogProvider(root).getReferenceManager(),
                               component.getBackground(), foreground);
    }
    else {
      painter.customizePainter(component, refs, null, component.getBackground(), foreground);
    }
  }

  public int getPreferredHeight() {
    return myComponent.getPreferredHeight();
  }

  public int getTooltipXCoordinate(int row) {
    return myComponent.getTooltipXCoordinate(getValue(myGraphTable.getModel().getValueAt(row, GraphTableModel.COMMIT_COLUMN)));
  }

  private static class MyComponent extends SimpleColoredRenderer {
    @NotNull private final VcsLogData myLogData;
    @NotNull private final VcsLogGraphTable myGraphTable;
    @NotNull private final GraphCellPainter myPainter;
    @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
    @NotNull private final ReferencePainter myReferencePainter =
      isRedesignedLabels() ? new LabelPainter() : new RectangleReferencePainter();
    @Nullable protected PaintInfo myGraphImage;
    @NotNull private Font myFont;

    private int myHeight;

    public MyComponent(@NotNull VcsLogData data, @NotNull GraphCellPainter painter, @NotNull VcsLogGraphTable table) {
      myLogData = data;
      myPainter = painter;
      myGraphTable = table;

      myIssueLinkRenderer = new IssueLinkRenderer(myLogData.getProject(), this);
      myFont = RectanglePainter.getFont();
      myHeight = calculateHeight();
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      int referencesSize = myReferencePainter.isLeftAligned() ? 0 : myReferencePainter.getSize().width;
      if (referencesSize > 0) referencesSize -= LabelPainter.GRADIENT_WIDTH;
      return new Dimension(preferredSize.width + referencesSize, getPreferredHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      int graphImageWidth = (myGraphImage != null) ? myGraphImage.getWidth() : 0;

      if (!myReferencePainter.isLeftAligned()) {
        int start = Math.max(graphImageWidth, getWidth() - myReferencePainter.getSize().width);
        myReferencePainter.paint((Graphics2D)g, start, 0, getHeight());
      }
      else {
        myReferencePainter.paint((Graphics2D)g, graphImageWidth, 0, getHeight());
      }

      if (myGraphImage != null) {
        UIUtil.drawImage(g, myGraphImage.getImage(), 0, 0, null);
      }
      else { // TODO temporary diagnostics: why does graph sometimes disappear
        LOG.error("Image is null");
      }
    }

    public void customize(@NotNull GraphCommitCell cell, boolean isSelected, boolean hasFocus, int row, int column) {
      clear();
      setPaintFocusBorder(hasFocus && myGraphTable.getCellSelectionEnabled());
      acquireState(myGraphTable, isSelected, hasFocus, row, column);
      getCellState().updateRenderer(this);

      myGraphImage = getGraphImage(cell.getPrintElements());

      int graphPadding;
      if (myGraphImage != null) {
        graphPadding = myGraphImage.getWidth();
        if (graphPadding < 2) {  // TODO temporary diagnostics: why does graph sometimes disappear
          LOG.error("Too small image width: " + graphPadding);
        }
      }
      else {
        graphPadding = 0;
      }

      SimpleTextAttributes style = myGraphTable.applyHighlighters(this, row, column, hasFocus, isSelected);

      Collection<VcsRef> refs = cell.getRefsToThisCommit();
      Color foreground = ObjectUtils.assertNotNull(myGraphTable.getBaseStyle(row, column, hasFocus, isSelected).getForeground());
      customizeRefsPainter(myReferencePainter, refs, foreground, myLogData, this);

      setBorder(null);
      append("");
      appendTextPadding(graphPadding + (myReferencePainter.isLeftAligned() ? myReferencePainter.getSize().width : 0));
      myIssueLinkRenderer.appendTextWithLinks(cell.getText(), style);
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

    @Nullable
    private PaintInfo getGraphImage(@NotNull Collection<? extends PrintElement> printElements) {
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
      BufferedImage image = UIUtil.createImage((int)(PaintParameters.getNodeWidth(myGraphTable.getRowHeight()) * (maxIndex + 2)),
                                               myGraphTable.getRowHeight(),
                                               BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      myPainter.draw(g2, printElements);

      int width = (int)(maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight()));
      return new PaintInfo(image, width);
    }

    public int getTooltipXCoordinate(@NotNull GraphCommitCell cell) {
      Collection<VcsRef> refs = cell.getRefsToThisCommit();
      if (!refs.isEmpty()) {
        customizeRefsPainter(myReferencePainter, refs, getForeground(), myLogData, this);
        TableColumn commitColumn = myGraphTable.getColumnModel().getColumn(GraphTableModel.COMMIT_COLUMN);
        return commitColumn.getWidth() - (myReferencePainter.getSize().width - LabelPainter.GRADIENT_WIDTH) / 2;
      }
      return -1;
    }
  }

  private static class PaintInfo {
    private int myWidth;
    @NotNull private Image myImage;

    PaintInfo(@NotNull Image image, int width) {
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

  private class FadeOutPainter {
    @NotNull private final LabelPainter myEmptyPainter = new LabelPainter();
    private int myWidth = LabelPainter.GRADIENT_WIDTH;

    public void customize(@NotNull Collection<VcsRef> currentRefs, int row, int column, @NotNull JTable table, @NotNull Color foreground) {
      myWidth = 0;

      if (currentRefs.isEmpty()) {
        int prevWidth = 0;
        if (row > 0) {
          GraphCommitCell commitCell = getValue(table.getValueAt(row - 1, column));
          customizeRefsPainter(myEmptyPainter, commitCell.getRefsToThisCommit(), foreground, myLogData, myComponent);
          prevWidth = myEmptyPainter.getSize().width;
        }

        int nextWidth = 0;
        if (row < table.getRowCount() - 1) {
          GraphCommitCell commitCell = getValue(table.getValueAt(row + 1, column));
          customizeRefsPainter(myEmptyPainter, commitCell.getRefsToThisCommit(), foreground, myLogData, myComponent);
          nextWidth = myEmptyPainter.getSize().width;
        }

        if (row == 0 && table.getRowCount() == 1) {
          customizeRefsPainter(myEmptyPainter, ContainerUtil.emptyList(), foreground, myLogData, myComponent);
        }
        myWidth = Math.max(Math.max(prevWidth, nextWidth), LabelPainter.GRADIENT_WIDTH);
      }
    }

    public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);

      myEmptyPainter.paintFadeOut(g2, x, y, myWidth, height);

      config.restore();
    }

    public int getWidth() {
      return myWidth;
    }
  }
}
