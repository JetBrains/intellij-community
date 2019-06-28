// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface SortableColumnModel {
  ColumnInfo[] getColumnInfos();

  void setSortable(boolean aBoolean);

  boolean isSortable();

  Object getRowValue(int row);

  @Nullable
  RowSorter.SortKey getDefaultSortKey();
}
