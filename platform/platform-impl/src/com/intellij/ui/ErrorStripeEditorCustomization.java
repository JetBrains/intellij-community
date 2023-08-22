// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ErrorStripeEditorCustomization extends SimpleEditorCustomization {

  public static final ErrorStripeEditorCustomization ENABLED = new ErrorStripeEditorCustomization(true);
  public static final ErrorStripeEditorCustomization DISABLED = new ErrorStripeEditorCustomization(false);

  private ErrorStripeEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    if (isEnabled()) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
      editor.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    }
    else {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(false);
    }
  }
}
