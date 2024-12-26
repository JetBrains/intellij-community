// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
public final class GStringTypedActionHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c != '{' || !HandlerUtils.canBeInvoked(editor, project)) {
      return Result.CONTINUE;
    }

    if (!(file instanceof GroovyFile)) return Result.CONTINUE;

    int caret = editor.getCaretModel().getOffset();
    final EditorHighlighter highlighter = editor.getHighlighter();
    if (caret < 1) return Result.CONTINUE;

    HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    if (iterator.getTokenType() != GroovyTokenTypes.mLCURLY) return Result.CONTINUE;
    iterator.retreat();
    if (iterator.atEnd() || iterator.getTokenType() != GroovyTokenTypes.mDOLLAR) return Result.CONTINUE;
    iterator.advance();
    if (iterator.atEnd()) return Result.CONTINUE;
    iterator.advance();
    if (iterator.getTokenType() != GroovyTokenTypes.mGSTRING_BEGIN) return Result.CONTINUE;

    editor.getDocument().insertString(caret, "}");
    return Result.STOP;
  }
}
