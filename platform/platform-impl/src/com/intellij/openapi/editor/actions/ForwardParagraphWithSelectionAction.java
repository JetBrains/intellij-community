// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.actionSystem.EditorAction;

/**
 * Emulates Emacs 'forward-paragraph' action
 */
public class ForwardParagraphWithSelectionAction extends EditorAction {
  public ForwardParagraphWithSelectionAction() {
    super(new ForwardParagraphAction.Handler(true));
  }
}
