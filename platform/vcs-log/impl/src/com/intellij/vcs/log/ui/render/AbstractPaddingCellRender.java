package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class AbstractPaddingCellRender extends ColoredTableCellRenderer {

  public static final Color MARKED_BACKGROUND = new Color(200, 255, 250);

  @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
  @Nullable private Object myValue;

  protected AbstractPaddingCellRender(@NotNull Project project) {
    myIssueLinkRenderer = new IssueLinkRenderer(project, this);
  }

  protected abstract int getLeftPadding(JTable table, Object value);

  @NotNull
  protected abstract String getCellText(@Nullable Object value);

  protected abstract void additionPaint(Graphics g, Object value);

  public static boolean isMarked(@Nullable Object value) {
    if (!(value instanceof GraphCommitCell)) {
      return false;
    }
    GraphCommitCell cell = (GraphCommitCell)value;
    for (SpecialPrintElement printElement : cell.getPrintCell().getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE && printElement.isMarked()) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value == null) {
      return;
    }
    myValue = value;
    append("");
    appendFixedTextFragmentWidth(getLeftPadding(table, value));
    myIssueLinkRenderer.appendTextWithLinks(getCellText(value));
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    additionPaint(g, myValue);
  }
}
