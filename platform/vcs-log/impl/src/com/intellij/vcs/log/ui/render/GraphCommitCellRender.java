package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.GraphCellPainter;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.Collection;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;
import static com.intellij.vcs.log.graph.render.PrintParameters.WIDTH_NODE;

/**
 * @author erokhins
 */
public class GraphCommitCellRender extends AbstractPaddingCellRender {

  @NotNull private final GraphCellPainter graphPainter;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final RefPainter refPainter;

  public GraphCommitCellRender(@NotNull GraphCellPainter graphPainter, @NotNull VcsLogDataHolder logDataHolder,
                               @NotNull VcsLogColorManager colorManager) {
    this.graphPainter = graphPainter;
    myDataHolder = logDataHolder;
    refPainter = new RefPainter(colorManager, false);
  }

  @Override
  protected int getLeftPadding(JTable table, @Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;

    if (cell == null) {
      return 0;
    }

    FontRenderContext fontContext = ((Graphics2D)table.getGraphics()).getFontRenderContext();
    int refPadding = refPainter.padding(cell.getRefsToThisCommit(), fontContext);

    int countCells = cell.getPrintCell().countCell();
    int graphPadding = countCells * WIDTH_NODE;

    return refPadding + graphPadding;
  }

  @NotNull
  protected String getCellText(@Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;
    if (cell == null) {
      return "!!! No cell for value: " + value;
    }
    else {
      return cell.getText();
    }
  }

  @Override
  protected void additionPaint(Graphics g, @Nullable Object value) {
    GraphCommitCell cell = (GraphCommitCell)value;
    if (cell == null) {
      return;
    }

    BufferedImage image = UIUtil.createImage(1000, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    g2.setBackground(new Color(0, 0, 0, 0));

    graphPainter.draw(g2, cell.getPrintCell());

    int countCells = cell.getPrintCell().countCell();
    int padding = countCells * WIDTH_NODE;
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      VirtualFile root = refs.iterator().next().getRoot(); // all refs are from the same commit => they have the same root
      refs = myDataHolder.getLogProvider(root).getReferenceManager().sort(refs);
    }
    refPainter.draw(g2, refs, padding, -1); // TODO think how to behave if there are too many refs here (even if tags are collapsed)

    g.drawImage(image, 0, 0, null);
  }
}
