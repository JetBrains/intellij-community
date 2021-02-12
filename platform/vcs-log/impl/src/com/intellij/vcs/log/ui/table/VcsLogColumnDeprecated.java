// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.vcs.log.ui.table.column.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @see VcsLogColumn
 * @deprecated This class is used only for moving old order and width settings of Log columns.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
public enum VcsLogColumnDeprecated {
  ROOT,
  COMMIT,
  AUTHOR,
  DATE,
  HASH;

  private static final Map<Integer, VcsLogColumn<?>> OLD_COLUMNS_MAPPING = new HashMap<>();

  static {
    OLD_COLUMNS_MAPPING.put(ROOT.ordinal(), Root.INSTANCE);
    OLD_COLUMNS_MAPPING.put(COMMIT.ordinal(), Commit.INSTANCE);
    OLD_COLUMNS_MAPPING.put(AUTHOR.ordinal(), Author.INSTANCE);
    OLD_COLUMNS_MAPPING.put(DATE.ordinal(), Date.INSTANCE);
    OLD_COLUMNS_MAPPING.put(HASH.ordinal(), Hash.INSTANCE);
  }

  @NotNull
  public static VcsLogColumn<?> getVcsLogColumnEx(int index) {
    return OLD_COLUMNS_MAPPING.get(index);
  }
}
