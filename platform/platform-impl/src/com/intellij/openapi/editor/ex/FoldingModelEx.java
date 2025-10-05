// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public interface FoldingModelEx extends FoldingModel {
  void setFoldingEnabled(boolean isEnabled);
  boolean isFoldingEnabled();

  @Nullable FoldRegion getFoldingPlaceholderAt(@NotNull Point p);

  boolean intersectsRegion(int startOffset, int endOffset);

  /**
   * Returns an index in an array returned by {@link #fetchTopLevel()} method, for the last folding region lying entirely before given
   * offset (region can touch given offset at its right edge).
   */
  int getLastCollapsedRegionBefore(int offset);

  @Nullable
  TextAttributes getPlaceholderAttributes();

  FoldRegion @Nullable [] fetchTopLevel();

  /**
   * @param neverExpands If {@code true}, the created region is created in the collapsed state, and cannot be expanded
   *                     ({@link FoldRegion#setExpanded(boolean)} does nothing for it). No marker will be displayed in gutter for such a
   *                     region. 'Never-expanding' fold region cannot be part of a {@link FoldingGroup}.
   */
  @Nullable
  FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, @Nullable FoldingGroup group,
                              boolean neverExpands);

  void addListener(@NotNull FoldingListener listener, @NotNull Disposable parentDisposable);

  void clearFoldRegions();

  void rebuild();

  @NotNull
  List<FoldRegion> getGroupedRegions(FoldingGroup group);

  void clearDocumentRangesModificationStatus();

  boolean hasDocumentRegionChangedFor(@NotNull FoldRegion region);

  @NotNull List<@NotNull FoldRegion> getRegionsOverlappingWith(int startOffset, int endOffset);
}
