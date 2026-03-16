// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.ui;

import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nullable;

/**
 * {@link XSourcePosition} can be marked with this interface to highlight exact range in the editor when execution point is reached.
 */
public interface ExecutionPointHighlighterProvider {
  @Nullable
  TextRange getHighlightRange();
}
