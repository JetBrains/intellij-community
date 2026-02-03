// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link RangeMarkerTree} with intervals which are not collected when no one holds a reference to them.
 */
@ApiStatus.Internal
public class HardReferencingRangeMarkerTree<T extends RangeMarkerImpl> extends RangeMarkerTree<T> {
  HardReferencingRangeMarkerTree(@NotNull Document document) {
    super(document);
  }

  @Override
  protected boolean keepIntervalOnWeakReference(@NotNull T interval) {
    return false;
  }

}
