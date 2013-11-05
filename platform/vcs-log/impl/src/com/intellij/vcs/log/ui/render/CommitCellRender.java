package com.intellij.vcs.log.ui.render;

import com.intellij.vcs.log.graph.render.CommitCell;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;

/**
 * @author erokhins
 */
public class CommitCellRender extends AbstractPaddingCellRender {

  @NotNull private final RefPainter refPainter;

  public CommitCellRender(@NotNull VcsLogColorManager colorManager) {
    refPainter = new RefPainter(colorManager, false);
  }

  @Override
  protected int getLeftPadding(JTable table, Object value) {
    CommitCell cell = getAssertCommitCell(value);

    FontRenderContext fontContext = ((Graphics2D)table.getGraphics()).getFontRenderContext();
    return refPainter.padding(cell.getRefsToThisCommit(), fontContext);
  }

  private static CommitCell getAssertCommitCell(Object value) {
    assert value instanceof CommitCell : "Value of incorrect class was supplied: " + value;
    return (CommitCell)value;
  }

  @NotNull
  @Override
  protected String getCellText(Object value) {
    if (value == null) {
      return "";
    }
    CommitCell cell = getAssertCommitCell(value);
    return cell.getText();
  }

  @Override
  protected void additionPaint(Graphics g, Object value) {
    CommitCell cell = getAssertCommitCell(value);
    Graphics2D g2 = (Graphics2D)g;
    refPainter.draw(g2, cell.getRefsToThisCommit(), 0, -1);
  }
}
