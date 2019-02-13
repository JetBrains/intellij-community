// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * @see HardReferencingRangeMarkerTree
 */
class RangeMarkerWithGetterImpl extends RangeMarkerImpl implements Getter<RangeMarkerWithGetterImpl> {
  RangeMarkerWithGetterImpl(@NotNull DocumentEx document, int start, int end, boolean register) {
    super(document, start, end, register, true);
  }

  @Override
  public final RangeMarkerWithGetterImpl get() {
    return this;
  }
}
