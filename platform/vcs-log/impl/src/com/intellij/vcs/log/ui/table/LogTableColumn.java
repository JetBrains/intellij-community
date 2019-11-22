// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Do not reorder: this might affect serialization. Add new columns at the end only.
 * If you want to tweak the order of dynamic columns in menus, change {@link #DYNAMIC_COLUMNS} list.
 * If you want to tweak the default order of columns, change {@link #getDefaultOrder()} method.
 */
public enum LogTableColumn {
  ROOT("", FilePath.class),
  COMMIT("Subject", GraphCommitCell.class),
  AUTHOR("Author", String.class),
  DATE("Date", String.class),
  HASH("Hash", String.class);

  public static final List<LogTableColumn> DYNAMIC_COLUMNS = ContainerUtil.immutableList(AUTHOR, DATE, HASH);
  private static final LogTableColumn[] COLUMNS = values(); // to reduce copying overhead

  private final String myName;
  private final Class<?> myContentClass;

  LogTableColumn(String name, Class<?> contentClass) {
    myName = name;
    myContentClass = contentClass;
  }

  public boolean isDynamic() {
    return compareTo(COMMIT) > 0;
  }

  public String getName() {
    return myName;
  }

  /**
   * @return stable name (to identify column in statistics)
   */
  public String getStableName() {
    return myName.toLowerCase(Locale.ROOT);
  }

  Class<?> getContentClass() {
    return myContentClass;
  }

  /**
   * @return list of indexes which correspond to default column order
   */
  public static List<Integer> getDefaultOrder() {
    return StreamEx.of(ROOT, AUTHOR, DATE, COMMIT).map(LogTableColumn::ordinal).toImmutableList();
  }

  static boolean isValidColumnOrder(@NotNull List<Integer> columnOrder) {
    int columnCount = count();
    if (!columnOrder.contains(ROOT.ordinal())) return false;
    if (!columnOrder.contains(COMMIT.ordinal())) return false;
    for (Integer index : columnOrder) {
      if (index == null || index < 0 || index >= columnCount) return false;
    }
    return true;
  }

  @NotNull
  public static LogTableColumn fromOrdinal(int index) {
    return COLUMNS[index];
  }

  public static int count() {
    return COLUMNS.length;
  }
}
