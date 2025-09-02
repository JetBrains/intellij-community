// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class CommonBreakpointGutterIconRenderer extends GutterIconRenderer {
  @Override
  public @NotNull Alignment getAlignment() {
    return ExperimentalUI.isNewUI() && EditorUtil.isBreakPointsOnLineNumbers() ? Alignment.LINE_NUMBERS : Alignment.RIGHT;
  }
}
