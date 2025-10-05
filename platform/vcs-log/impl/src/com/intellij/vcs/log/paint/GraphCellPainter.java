// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.paint;

import com.intellij.ui.scale.ScaleContext;
import com.intellij.vcs.log.graph.PrintElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

import static com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle;

/**
 * @author erokhins
 */
@ApiStatus.Internal
public interface GraphCellPainter {

  void paint(@NotNull Graphics2D g2, @NotNull VcsCommitStyle commitStyle, @NotNull Collection<? extends PrintElement> printElements);

  @Nullable
  PrintElement getElementUnderCursor(@NotNull ScaleContext scaleContext, @NotNull Collection<? extends PrintElement> printElements, int x, int y);
}

