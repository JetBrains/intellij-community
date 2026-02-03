// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Emulates Emacs 'forward-paragraph' action
 */
@ApiStatus.Internal
public final class ForwardParagraphWithSelectionAction extends EditorAction {
  public ForwardParagraphWithSelectionAction() {
    super(new ForwardParagraphAction.Handler(true));
  }
}
