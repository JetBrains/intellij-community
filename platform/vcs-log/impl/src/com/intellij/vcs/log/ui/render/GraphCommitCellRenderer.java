package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

public class GraphCommitCellRenderer extends ColoredTableCellRenderer {
  private static final Logger LOG = Logger.getInstance(GraphCommitCellRenderer.class);
  private static final int MAX_GRAPH_WIDTH = 10;

  private static final int VERTICAL_PADDING = JBUI.scale(7);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final GraphCellPainter myPainter;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
  @NotNull private final TagLabelPainter myTextLabelPainter = new TagLabelPainter();

  @Nullable private PaintInfo myGraphImage;
  @NotNull private Font myFont;
  private int myHeight;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table) {
    myLogData = logData;
    myPainter = painter;
    myGraphTable = table;
    myIssueLinkRenderer = new IssueLinkRenderer(myLogData.getProject(), this);
    myFont = TextLabelPainter.getFont();
    myHeight = calculateHeight();
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    return new Dimension(preferredSize.width, getPreferredHeight());
  }

  public int getPreferredHeight() {
    Font font = TextLabelPainter.getFont();
    if (myFont != font) {
      myFont = font;
      myHeight = calculateHeight();
    }
    return myHeight;
  }

  private int calculateHeight() {
    return Math.max(myTextLabelPainter.getSize().height, getFontMetrics(myFont).getHeight() + VERTICAL_PADDING);
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);

    myTextLabelPainter.paint((Graphics2D)g, getWidth() - myTextLabelPainter.getSize().width, 0, getHeight());

    if (myGraphImage != null) {
      UIUtil.drawImage(g, myGraphImage.getImage(), 0, 0, null);
    }
    else { // TODO temporary diagnostics: why does graph sometimes disappear
      LOG.error("Image is null");
    }
  }

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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

    setBorder(null);
    append("");
    appendTextPadding(graphPadding);
    myIssueLinkRenderer.appendTextWithLinks(cell.getText(), myGraphTable.applyHighlighters(this, row, column, "", hasFocus, isSelected));

    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    Color foreground = ObjectUtils.assertNotNull(myGraphTable.getBaseStyle(row, column, "", hasFocus, isSelected).getForeground());
    if (refs.isEmpty()) {
      myTextLabelPainter.customizePainter(this, refs, getBackground(), foreground);
    }
    else {
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(refs)).getRoot();
      myTextLabelPainter
        .customizePainter(this,
                          ContainerUtil.sorted(refs, myLogData.getLogProvider(root).getReferenceManager().getLabelsOrderComparator()),
                          getBackground(), foreground);
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
}
