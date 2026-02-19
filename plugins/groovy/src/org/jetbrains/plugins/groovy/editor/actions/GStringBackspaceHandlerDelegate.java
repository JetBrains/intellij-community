// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.editor.actions;

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
final class GStringBackspaceHandlerDelegate extends BackspaceHandlerDelegate {
  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    if (c != '{') return;

    if (!(file instanceof GroovyFile)) return;

    final int offset = editor.getCaretModel().getOffset();

    final EditorHighlighter highlighter = editor.getHighlighter();
    if (offset < 1) return;

    HighlighterIterator iterator = highlighter.createIterator(offset);
    if (iterator.getTokenType() != GroovyTokenTypes.mRCURLY) return;
    iterator.retreat();
    if (iterator.getStart() < 1 || iterator.getTokenType() != GroovyTokenTypes.mLCURLY) return;

    editor.getDocument().deleteString(offset, offset + 1);
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    return false;
  }
}
