// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.codeInsight.editorActions.TypedHandlerUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.INVALID_INSIDE_REFERENCE;

public final class GroovyBackspaceHandler extends BackspaceHandlerDelegate {
  private boolean myToDeleteGt;

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset() - 1;
    myToDeleteGt = c =='<' && file instanceof GroovyFile && GroovyTypedHandler.isAfterClassLikeIdentifier(offset, editor);
  }

  @Override
  public boolean charDeleted(final char c, final @NotNull PsiFile file, final @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    final CharSequence chars = editor.getDocument().getCharsSequence();
    if (editor.getDocument().getTextLength() <= offset) return false; //virtual space after end of file

    char c1 = chars.charAt(offset);
    if (c == '<' && myToDeleteGt) {
      if (c1 != '>') return true;
      TypedHandlerUtil.handleGenericLTDeletion(editor, offset, GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, INVALID_INSIDE_REFERENCE);
      return true;
    }
    return false;
  }
}
