// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap.mapping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface SoftWrapAwareDocumentParsingListener {

  /**
   * Notifies current listener that particular document region re-parsing is about to begin.
   * 
   * @param event   object that contains information about re-parsed document region
   */
  void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event);

  /**
   * Notifies current listener that particular document region re-parsing has just finished.
   *
   * @param event   object that contains information about re-parsed document region
   */
  void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event);

  /**
   * Notifies current listener that all dirty regions for the current editor have been recalculated.
   * <p/>
   * It differs from {@link #onRecalculationEnd(IncrementalCacheUpdateEvent)} because there is a possible case that there
   * is more than one 'dirty' region which is necessary to recalculate.
   * {@link #onRecalculationEnd(IncrementalCacheUpdateEvent)} will be called after every region recalculation then
   * and current method will be called one time when all recalculations have been performed.
   */
  void recalculationEnds();
  
  /**
   * Callback for asking to drop all cached information (if any).
   */
  void reset();
}
