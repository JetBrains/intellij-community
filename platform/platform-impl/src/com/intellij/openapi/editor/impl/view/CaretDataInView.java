// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;

import java.util.List;


/**
 * Introduced within IDEA-94918 Option to show whitespaces only in selected (highlighted code)
 */
final class CaretDataInView {
  private final int[] selectionStarts;
  private final int[] selectionEnds;
  private final int caretCount;

  CaretDataInView(CaretModel caretModel, int startOffset, int endOffset) {
    List<Caret> carets = caretModel.getAllCarets();
    selectionStarts = new int[carets.size()];
    selectionEnds = new int[carets.size()];
    int i = 0;
    for (Caret caret : carets) {
      if (!caret.hasSelection()) continue;
      if (caret.getSelectionStart() >= endOffset || caret.getSelectionEnd() <= startOffset) {
        continue;
      }
      selectionStarts[i] = caret.getSelectionStart();
      selectionEnds[i] = caret.getSelectionEnd();
      ++i;
    }
    caretCount = i;
  }

  boolean isOffsetInSelection(int offset) {
    for (int i = 0; i < caretCount; ++i) {
      if (offset >= selectionStarts[i] && offset < selectionEnds[i]) {
        return true;
      }
    }
    return false;
  }
}
