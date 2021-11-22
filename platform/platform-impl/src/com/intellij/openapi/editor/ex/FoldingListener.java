// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for clients interested in folding processing.
 *
 * @author Denis Zhdanov
 */
public interface FoldingListener {

  /**
   * Informs that {@code 'collapsed'} state of given fold region is just changed, or that the given fold region has just been created.
   * <p/>
   * <b>Note:</b> listener should delay fold region state processing until {@link #onFoldProcessingEnd()} is called.
   * I.e. folding model may return inconsistent data between current moment and {@link #onFoldProcessingEnd()}.
   *
   * @param region    fold region that is just collapsed or expanded
   */
  default void onFoldRegionStateChange(@NotNull FoldRegion region) {
  }

  /**
   * Called when properties (size or gutter renderer) are changed for a {@link CustomFoldRegion}. This can happen as a result of explicit
   * {@link CustomFoldRegion#update()} call, or due to an implicit update (e.g. when appearance settings change). The changes can happen
   * outside of batch folding operation.
   */
  @ApiStatus.Experimental
  default void onCustomFoldRegionPropertiesChange(@NotNull CustomFoldRegion region,
                                                  @MagicConstant(flagsFromClass = ChangeFlags.class) int flags) {
  }

  /**
   * This method is called when the specified {@link FoldRegion} is about to become invalid. This can happen either due to explicit removal
   * of the region (using {@link FoldingModel#removeFoldRegion(FoldRegion)}, {@link FoldingModelEx#clearFoldRegions()} or
   * {@link FoldRegion#dispose()}), or as a result of document change.
   */
  @ApiStatus.Experimental
  default void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
  }

  /**
   * Informs that the given fold region is about to be removed (using {@link FoldingModel#removeFoldRegion(FoldRegion)} or
   * {@link FoldingModelEx#clearFoldRegions()}).
   */
  default void beforeFoldRegionRemoved(@NotNull FoldRegion region) {
  }

  /**
   * Invoked in a batch folding operation before any change is performed.
   */
  default void onFoldProcessingStart() {
  }

  /**
   * Informs that fold processing is done.
   */
  default void onFoldProcessingEnd() {
  }

  @ApiStatus.Experimental
  interface ChangeFlags {
    int WIDTH_CHANGED = 0x1;
    int HEIGHT_CHANGED = 0x2;
    int GUTTER_ICON_PROVIDER_CHANGED = 0x4;
  }
}
