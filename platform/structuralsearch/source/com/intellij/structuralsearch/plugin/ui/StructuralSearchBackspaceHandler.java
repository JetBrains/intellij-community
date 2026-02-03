// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchBackspaceHandler extends BackspaceHandlerDelegate {

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {}

  @Override
  public boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    if (editor.getUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY) == null ||
        !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      return false;
    }
    if (c == '$') {
      final Document document = editor.getDocument();
      final CharSequence text = document.getCharsSequence();
      final Caret caret = editor.getCaretModel().getCurrentCaret();
      final int offset = caret.getOffset();
      final LogicalPosition position = caret.getLogicalPosition();
      final int lineStart = document.getLineStartOffset(position.line);
      final int lineEnd = document.getLineEndOffset(position.line);
      if (text.length() > offset && text.charAt(offset) == '$' && StructuralSearchTypedHandler.hasOddDollar(text, lineStart, lineEnd)) {
        document.deleteString(offset, offset + 1);
      }
      return true;
    }
    return false;
  }
}
