// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Objects;

@ApiStatus.Internal
public abstract class TypeSafeTableCellRenderer<T> implements TableCellRenderer {

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    return getTableCellRendererComponentImpl(table, getValue(Objects.requireNonNull(value)), isSelected, hasFocus, row, column);
  }

  protected @NotNull T getValue(@NotNull Object value) {
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
