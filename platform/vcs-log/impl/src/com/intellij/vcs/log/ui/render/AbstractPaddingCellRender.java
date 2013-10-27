package com.intellij.vcs.log.ui.render;

import com.intellij.ui.JBColor;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author erokhins
 */
public abstract class AbstractPaddingCellRender implements TableCellRenderer {

  public static final Color MARKED_BACKGROUND = new Color(200, 255, 250);

  @NotNull private final ExtDefaultCellRender myCellRender = new ExtDefaultCellRender();

  protected abstract int getLeftPadding(JTable table, Object value);

  @NotNull
  protected abstract String getCellText(Object value);

  protected abstract void additionPaint(Graphics g, Object value);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                 boolean hasFocus, int row, int column) {
    return myCellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
  }

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


  private class ExtDefaultCellRender extends DefaultTableCellRenderer {
    private Object myValue;

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      myValue = value;
      super.getTableCellRendererComponent(table, getCellText(value), isSelected, hasFocus, row, column);
      setBackground(isSelected ? table.getSelectionBackground() : JBColor.WHITE);

      Border paddingBorder = BorderFactory.createEmptyBorder(0, getLeftPadding(table, value), 0, 0);
      setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));
      Color textColor = isSelected ? table.getSelectionForeground() : JBColor.BLACK;
      setForeground(textColor);

      return this;
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      additionPaint(g, myValue);
    }
  }
}
