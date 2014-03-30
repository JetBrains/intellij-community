package com.intellij.util.ui.table;

import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public abstract class IconTableCellRenderer<T> extends DefaultTableCellRenderer {
  public static final IconTableCellRenderer<Iconable> ICONABLE = new IconTableCellRenderer<Iconable>() {
    @Nullable
    @Override
    protected Icon getIcon(@NotNull Iconable value, JTable table, int row) {
      return value.getIcon(Iconable.ICON_FLAG_VISIBILITY);
    }
  };

  public static TableCellRenderer create(@NotNull final Icon icon) {
    return new IconTableCellRenderer() {
      @Nullable
      @Override
      protected Icon getIcon(@NotNull Object value, JTable table, int row) {
        return icon;
      }
    };
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
    super.getTableCellRendererComponent(table, value, selected, focus, row, column);
    //noinspection unchecked
    setIcon(value == null ? null : getIcon((T)value, table, row));
    if (isCenterAlignment()) {
      setHorizontalAlignment(CENTER);
      setVerticalAlignment(CENTER);
    }
    return this;
  }

  protected boolean isCenterAlignment() {
    return false;
  }

  @Nullable
  protected abstract Icon getIcon(@NotNull T value, JTable table, int row);
}