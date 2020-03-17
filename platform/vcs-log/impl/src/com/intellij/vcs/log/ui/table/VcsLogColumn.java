// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.text.DateTimeFormatManager;
import com.intellij.util.text.JBDateFormat;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Do not reorder: this might affect serialization. Add new columns at the end only.
 * If you want to tweak the order of dynamic columns in menus, change {@link #DYNAMIC_COLUMNS} list.
 * If you want to tweak the default order of columns, change the corresponding implementation of {@link com.intellij.vcs.log.impl.VcsLogUiProperties}.
 */
public enum VcsLogColumn {
  ROOT("", FilePath.class),
  COMMIT("Subject", GraphCommitCell.class),
  AUTHOR("Author", String.class),
  DATE("Date", String.class) {
    @Override
    public String getContentSample() {
      if (DateTimeFormatManager.getInstance().isPrettyFormattingAllowed()) return null;
      return JBDateFormat.getFormatter().formatDateTime(DateFormatUtil.getSampleDateTime());
    }
  },
  HASH("Hash", String.class) {
    @Override
    public String getContentSample() {
      return StringUtil.repeat("e", VcsLogUtil.SHORT_HASH_LENGTH);
    }
  };

  @NotNull public static final List<VcsLogColumn> DYNAMIC_COLUMNS = ContainerUtil.immutableList(AUTHOR, DATE, HASH);
  @NotNull private static final VcsLogColumn[] COLUMNS = values(); // to reduce copying overhead

  @NotNull private final String myName;
  @NotNull private final Class<?> myContentClass;

  VcsLogColumn(@NotNull String name, @NotNull Class<?> contentClass) {
    myName = name;
    myContentClass = contentClass;
  }

  public boolean isDynamic() {
    return DYNAMIC_COLUMNS.contains(this);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return stable name (to identify column in statistics)
   */
  @NotNull
  public String getStableName() {
    return myName.toLowerCase(Locale.ROOT);
  }

  @NotNull
  Class<?> getContentClass() {
    return myContentClass;
  }

  /**
   * @return content sample to estimate the width of the column,
   * or null if content width may vary significantly and width cannot be estimated from the sample
   */
  @Nullable
  public String getContentSample() {
    return null;
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
  public static VcsLogColumn fromOrdinal(int index) {
    return COLUMNS[index];
  }

  public static int count() {
    return COLUMNS.length;
  }
}
