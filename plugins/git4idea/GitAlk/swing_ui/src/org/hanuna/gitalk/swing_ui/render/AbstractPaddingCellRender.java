package org.hanuna.gitalk.swing_ui.render;

import org.hanuna.gitalk.ui.tables.GraphCommitCell;
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
  public static final Color MARKED_BACKGROUND = new Color(0xB6, 0xE4, 0xFF);

  private ExtDefaultCellRender cellRender = new ExtDefaultCellRender();

  protected abstract int getLeftPadding(JTable table, @Nullable Object value);

  protected abstract String getCellText(JTable table, @Nullable Object value);

  protected abstract void additionPaint(Graphics g, JTable table, @Nullable Object value);

  protected abstract boolean isMarked(JTable table, @Nullable Object value);

  protected abstract GraphCommitCell.Kind getKind(JTable table, @Nullable Object value);

  @Override
  public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    return cellRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
  }

  public class ExtDefaultCellRender extends DefaultTableCellRenderer {
    private JTable table;
    private Object value;

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      this.table = table;
      this.value = value;
      super.getTableCellRendererComponent(table, getCellText(table, value), isSelected, hasFocus, row, column);
      if (isMarked(table, value) && !isSelected) {
        setBackground(MARKED_BACKGROUND);
      }
      else {
        setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
      }
      Border paddingBorder = BorderFactory.createEmptyBorder(0, getLeftPadding(table, value), 0, 0);
      this.setBorder(BorderFactory.createCompoundBorder(this.getBorder(), paddingBorder));

      GraphCommitCell.Kind kind = getKind(table, value);
      switch (kind) {
        case NORMAL:
          setForeground(Color.BLACK);
          break;
        case PICK:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(Color.BLACK);
          break;
        case FIXUP:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(Color.DARK_GRAY);
          break;
        case REWORD:
          setFont(getFont().deriveFont(Font.BOLD));
          setForeground(Color.blue);
          break;
      }

      return this;
    }


    @Override
    public void paint(Graphics g) {
      super.paint(g);
      additionPaint(g, table, value);
    }
  }


}
