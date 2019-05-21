// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public abstract class TableViewModel<Item> extends AbstractTableModel implements SortableColumnModel {
  public abstract void setItems(@NotNull List<Item> items);
  @NotNull
  public abstract List<Item> getItems();
}