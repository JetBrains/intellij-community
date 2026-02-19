// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

public final class RightMarginEditorCustomization extends SimpleEditorCustomization {

  private final int myRightMarginColumns;

  public RightMarginEditorCustomization(boolean enabled, int rightMarginColumns) {
    super(enabled);
    myRightMarginColumns = rightMarginColumns;
  }

  public int getRightMarginColumns() {
    return myRightMarginColumns;
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setRightMarginShown(isEnabled());
    editor.getSettings().setRightMargin(getRightMarginColumns());
  }
}
