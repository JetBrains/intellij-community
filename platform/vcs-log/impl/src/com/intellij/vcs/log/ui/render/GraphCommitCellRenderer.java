package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableCell;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.frame.ReferencesPanel;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Map;

public class GraphCommitCellRenderer extends ColoredTableCellRenderer {
  private static final Logger LOG = Logger.getInstance(GraphCommitCellRenderer.class);
  private static final int MAX_GRAPH_WIDTH = 10;

  private static final int VERTICAL_PADDING = JBUI.scale(7);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final GraphCellPainter myPainter;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
  @NotNull private final ReferencePainter myReferencePainter =
    isRedesignedLabels() ? new LabelPainter() : new RectangleReferencePainter();
  @Nullable private final FadeOutPainter myFadeOutPainter = isRedesignedLabels() ? new FadeOutPainter() : null;

  @Nullable private PaintInfo myGraphImage;
  @NotNull private Font myFont;
  private int myHeight;
  private boolean myExpanded;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table) {
    myLogData = logData;
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

  public int getPreferredHeight() {
    Font font = RectanglePainter.getFont();
    if (myFont != font) {
      myFont = font;
      myHeight = calculateHeight();
    }
    return myHeight;
  }

  private int calculateHeight() {
    return Math.max(myReferencePainter.getSize().height, getFontMetrics(myFont).getHeight() + VERTICAL_PADDING);
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

    if (myFadeOutPainter != null) {
      if (!myExpanded) {
        int start = Math.max(graphImageWidth, getWidth() - myFadeOutPainter.getWidth());
        myFadeOutPainter.paint((Graphics2D)g, start, 0, getHeight());
      }
    }

    if (myGraphImage != null) {
      UIUtil.drawImage(g, myGraphImage.getImage(), 0, 0, null);
    }
    else { // TODO temporary diagnostics: why does graph sometimes disappear
      LOG.error("Image is null");
    }
  }

  @Override
  public void customizeCellRenderer(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value == null) {
      return;
    }

    GraphCommitCell cell = getAssertCommitCell(value);
    myGraphImage = getGraphImage(row);

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

    SimpleTextAttributes style = myGraphTable.applyHighlighters(this, row, column, "", hasFocus, isSelected);

    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    Color foreground = ObjectUtils.assertNotNull(myGraphTable.getBaseStyle(row, column, "", hasFocus, isSelected).getForeground());
    myExpanded = myGraphTable.getExpandableItemsHandler().getExpandedItems().contains(new TableCell(row, column));
    if (myFadeOutPainter != null) {
      myFadeOutPainter.customize(refs, row, column, table, foreground);
    }
    customizeRefsPainter(myReferencePainter, refs, foreground);

    setBorder(null);
    append("");
    appendTextPadding(graphPadding + (myReferencePainter.isLeftAligned() ? myReferencePainter.getSize().width : 0));
    myIssueLinkRenderer.appendTextWithLinks(cell.getText(), style);
  }

  private void customizeRefsPainter(@NotNull ReferencePainter painter, @NotNull Collection<VcsRef> refs, @NotNull Color foreground) {
    if (!refs.isEmpty()) {
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(refs)).getRoot();
      painter.customizePainter(this, refs, myLogData.getLogProvider(root).getReferenceManager(), getBackground(), foreground);
    }
    else {
      painter.customizePainter(this, refs, null, getBackground(), foreground);
    }
  }

  @Nullable
  private PaintInfo getGraphImage(int row) {
    VisibleGraph<Integer> graph = myGraphTable.getVisibleGraph();
    Collection<? extends PrintElement> printElements = graph.getRowInfo(row).getPrintElements();
    int maxIndex = 0;
    for (PrintElement printElement : printElements) {
      maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
    }
    maxIndex++;
    maxIndex = Math.max(maxIndex, Math.min(MAX_GRAPH_WIDTH, graph.getRecommendedWidth()));
    final BufferedImage image = UIUtil
      .createImage(PaintParameters.getNodeWidth(myGraphTable.getRowHeight()) * (maxIndex + 4), myGraphTable.getRowHeight(),
                   BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    myPainter.draw(g2, printElements);

    int width = maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight());
    return new PaintInfo(image, width);
  }

  private static GraphCommitCell getAssertCommitCell(Object value) {
    assert value instanceof GraphCommitCell : "Value of incorrect class was supplied: " + value;
    return (GraphCommitCell)value;
  }

  public static boolean isRedesignedLabels() {
    return Registry.is("vcs.log.labels.redesign");
  }

  @Nullable
  public JComponent getTooltip(@NotNull Object value, @NotNull Point point, int width) {
    if (!isRedesignedLabels()) return null;

    GraphCommitCell cell = getAssertCommitCell(value);
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      customizeRefsPainter(myReferencePainter, refs, getForeground());
      if (myReferencePainter.getSize().getWidth() - LabelPainter.GRADIENT_WIDTH >= width - point.getX()) {
        return new TooltipReferencesPanel(refs);
      }
    }
    return null;
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
          GraphCommitCell commitCell = getAssertCommitCell(table.getValueAt(row - 1, column));
          customizeRefsPainter(myEmptyPainter, commitCell.getRefsToThisCommit(), foreground);
          prevWidth = myEmptyPainter.getSize().width;
        }

        int nextWidth = 0;
        if (row < table.getRowCount() - 1) {
          GraphCommitCell commitCell = getAssertCommitCell(table.getValueAt(row + 1, column));
          customizeRefsPainter(myEmptyPainter, commitCell.getRefsToThisCommit(), foreground);
          nextWidth = myEmptyPainter.getSize().width;
        }

        if (row == 0 && table.getRowCount() == 1) {
          customizeRefsPainter(myEmptyPainter, ContainerUtil.emptyList(), foreground);
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

  private class TooltipReferencesPanel extends ReferencesPanel {
    private static final int REFS_LIMIT = 10;
    private boolean myHasGroupWithMultipleRefs;

    public TooltipReferencesPanel(@NotNull Collection<VcsRef> refs) {
      super(new VerticalFlowLayout(JBUI.scale(H_GAP), JBUI.scale(V_GAP)), REFS_LIMIT);
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(refs)).getRoot();
      setReferences(
        ContainerUtil.sorted(refs, myLogData.getLogProvider(root).getReferenceManager().getLabelsOrderComparator()));
    }

    @Override
    public void update() {
      super.update();
      myHasGroupWithMultipleRefs = false;
      for (Map.Entry<VcsRefType, Collection<VcsRef>> typeAndRefs : myGroupedVisibleReferences.entrySet()) {
        if (typeAndRefs.getValue().size() > 1) {
          myHasGroupWithMultipleRefs = true;
        }
      }
    }

    @NotNull
    @Override
    protected Font getLabelsFont() {
      return myReferencePainter.getReferenceFont();
    }

    @Nullable
    @Override
    protected Icon createIcon(@NotNull VcsRefType type, @NotNull Collection<VcsRef> refs, int refIndex, int height) {
      if (refIndex == 0) {
        Color color = type.getBackgroundColor();
        return new LabelIcon(height, getBackground(),
                             refs.size() > 1 ? new Color[]{color, color} : new Color[]{color}) {
          @Override
          public int getIconWidth() {
            return getWidth(myHasGroupWithMultipleRefs ? 2 : 1);
          }
        };
      }
      return createEmptyIcon(height);
    }

    @NotNull
    private Icon createEmptyIcon(int height) {
      return EmptyIcon.create(height + (height / 4), height);
    }

    @NotNull
    @Override
    protected JBLabel createRestLabel(int restSize) {
      String gray = ColorUtil.toHex(UIManager.getColor("Button.disabledText"));
      return createLabel("<html><font color=\"#" + gray + "\">... " + restSize + " more in details pane</font></html>",
                         createEmptyIcon(getIconHeight()));
    }
  }
}
