// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * {@link RangeMarkerTree} with intervals which are not collected when no one holds a reference to them.
 */
class HardReferencingRangeMarkerTree<T extends RangeMarkerImpl> extends RangeMarkerTree<T> {
  HardReferencingRangeMarkerTree(@NotNull Document document) {
    super(document);
  }

  @Override
  protected boolean keepIntervalOnWeakReference(@NotNull T interval) {
    return false;
  }

}
