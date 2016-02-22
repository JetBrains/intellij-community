package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GraphCommitCellRender extends ColoredTableCellRenderer {

  private static final Logger LOG = Logger.getInstance(GraphCommitCellRender.class);

  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final GraphCellPainter myPainter;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final TextLabelPainter myTextLabelPainter;
  @NotNull private final IssueLinkRenderer myIssueLinkRenderer;

  @Nullable private PaintInfo myGraphImage;
  @Nullable private Collection<VcsRef> myRefs;
  @NotNull private Font myFont;
  private int myHeight;

  public GraphCommitCellRender(@NotNull VcsLogDataHolder dataHolder, @NotNull GraphCellPainter painter, @NotNull VcsLogGraphTable table) {
    myDataHolder = dataHolder;
    myPainter = painter;
    myGraphTable = table;
    myTextLabelPainter = TextLabelPainter.createPainter(false);
    myIssueLinkRenderer = new IssueLinkRenderer(dataHolder.getProject(), this);
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
    return myTextLabelPainter.calculateSize("", getFontMetrics(myFont)).height + 4;
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myRefs != null) {
      int paddingX = (myGraphImage != null ? myGraphImage.getWidth() : 0) + PaintParameters.LABEL_PADDING;
      Map<String, Color> labelsForReferences = collectLabelsForRefs(myRefs);
      for (Map.Entry<String, Color> entry : labelsForReferences.entrySet()) {
        Dimension size = myTextLabelPainter.calculateSize(entry.getKey(), g.getFontMetrics(TextLabelPainter.getFont()));
        int paddingY = (myGraphTable.getRowHeight() - size.height) / 2;
        myTextLabelPainter.paint((Graphics2D)g, entry.getKey(), paddingX, paddingY, entry.getValue());
        paddingX += size.width + PaintParameters.LABEL_PADDING;
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
  protected void customizeCellRenderer(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value == null) {
      return;
    }

    GraphCommitCell cell = getAssertCommitCell(value);
    myGraphImage = getGraphImage(row);
    myRefs = cell.getRefsToThisCommit();

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
    int textPadding = graphPadding + calculateReferencePadding(myRefs);

    setBorder(null);
    append("");
    appendTextPadding(textPadding);
    myIssueLinkRenderer.appendTextWithLinks(cell.getText(), myGraphTable.applyHighlighters(this, row, column, "", hasFocus, isSelected));
  }

  @Nullable
  private PaintInfo getGraphImage(int row) {
    Collection<? extends PrintElement> printElements = myGraphTable.getVisibleGraph().getRowInfo(row).getPrintElements();
    int maxIndex = 0;
    for (PrintElement printElement : printElements) {
      maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
    }
    maxIndex++;
    final BufferedImage image = UIUtil
      .createImage(PaintParameters.getNodeWidth(myGraphTable.getRowHeight()) * (maxIndex + 4), myGraphTable.getRowHeight(),
                   BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    myPainter.draw(g2, printElements);

    final int width = maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight());
    return new PaintInfo(image, width);
  }

  private static GraphCommitCell getAssertCommitCell(Object value) {
    assert value instanceof GraphCommitCell : "Value of incorrect class was supplied: " + value;
    return (GraphCommitCell)value;
  }

  @NotNull
  private Map<String, Color> collectLabelsForRefs(@NotNull Collection<VcsRef> refs) {
    if (refs.isEmpty()) {
      return Collections.emptyMap();
    }
    VirtualFile root = refs.iterator().next().getRoot(); // all refs are from the same commit => they have the same root
    refs = ContainerUtil.sorted(refs, myDataHolder.getLogProvider(root).getReferenceManager().getLabelsOrderComparator());
    List<VcsRef> branches = getBranches(refs);
    Collection<VcsRef> tags = ContainerUtil.subtract(refs, branches);
    return getLabelsForRefs(branches, tags);
  }

  private int calculateReferencePadding(@NotNull Collection<VcsRef> references) {
    if (references.isEmpty()) return 0;

    int paddingX = 2 * PaintParameters.LABEL_PADDING;
    for (String label : collectLabelsForRefs(references).keySet()) {
      Dimension size = myTextLabelPainter.calculateSize(label, this.getFontMetrics(TextLabelPainter.getFont()));
      paddingX += size.width + PaintParameters.LABEL_PADDING;
    }
    return paddingX;
  }

  @NotNull
  private static Map<String, Color> getLabelsForRefs(@NotNull List<VcsRef> branches, @NotNull Collection<VcsRef> tags) {
    Map<String, Color> labels = ContainerUtil.newLinkedHashMap();
    for (VcsRef branch : branches) {
      labels.put(branch.getName(), branch.getType().getBackgroundColor());
    }
    if (!tags.isEmpty()) {
      VcsRef firstTag = tags.iterator().next();
      Color color = firstTag.getType().getBackgroundColor();
      if (tags.size() > 1) {
        labels.put(firstTag.getName() + " +", color);
      }
      else {
        labels.put(firstTag.getName(), color);
      }
    }
    return labels;
  }

  private static List<VcsRef> getBranches(Collection<VcsRef> refs) {
    return ContainerUtil.filter(refs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });
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
