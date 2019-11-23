// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

public interface VcsLogCellController {

  @Nullable
  Cursor performMouseClick(int row, @NotNull MouseEvent e);

  @Nullable
  Cursor performMouseMove(int row, @NotNull MouseEvent e);

  default boolean shouldSelectCell(int row, @NotNull MouseEvent e) {
    return false;
  }
}
