// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.render;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Objects;

public abstract class TypeSafeTableCellRenderer<T> implements TableCellRenderer {

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    return getTableCellRendererComponentImpl(table, getValue(Objects.requireNonNull(value)), isSelected, hasFocus, row, column);
  }

  @NotNull
  protected T getValue(@NotNull Object value) {
    //noinspection unchecked
    return (T)value;
  }


  protected abstract Component getTableCellRendererComponentImpl(@NotNull JTable table,
                                                                 @NotNull T value,
                                                                 boolean isSelected,
                                                                 boolean hasFocus,
                                                                 int row,
                                                                 int column);
}
