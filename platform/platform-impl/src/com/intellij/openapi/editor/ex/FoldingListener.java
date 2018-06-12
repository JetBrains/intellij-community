// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for clients interested in folding processing.
 *
 * @author Denis Zhdanov
 * @since Sep 8, 2010 11:20:28 AM
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
   * Informs that the given fold region is about to be removed.
   */
  default void beforeFoldRegionRemoved(@NotNull FoldRegion region) {
  }

  /**
   * Informs that fold processing is done.
   */
  default void onFoldProcessingEnd() {
  }
}
