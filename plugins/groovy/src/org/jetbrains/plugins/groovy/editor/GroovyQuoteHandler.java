// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ven
 */
public class GroovyQuoteHandler implements MultiCharQuoteHandler {

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == GroovyTokenTypes.mGSTRING_END) return true;
    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL || tokenType == GroovyTokenTypes.mGSTRING_LITERAL) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1 ||
             end - start >= 5 && offset >= end - 3;
    }
    if (tokenType == GroovyTokenTypes.mREGEX_END) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }
    return false;
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == GroovyTokenTypes.mGSTRING_BEGIN || tokenType == GroovyTokenTypes.mREGEX_BEGIN) return true;
    if (tokenType == GroovyTokenTypes.mGSTRING_LITERAL || tokenType == GroovyTokenTypes.mSTRING_LITERAL) {
      int start = iterator.getStart();
      return offset == start;
    }
    return false;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL || tokenType == GroovyTokenTypes.mGSTRING_BEGIN || tokenType ==
                                                                                                         GroovyTokenTypes.mGSTRING_LITERAL || tokenType ==
                                                                                                                                              GroovyTokenTypes.mGSTRING_CONTENT) {
      final Document document = iterator.getDocument();
      if (document == null) return false;
      final String literal = document.getText().substring(iterator.getStart(), offset + 1);
      if ("'''".equals(literal) || "\"\"\"".equals(literal) || "'".equals(literal) || "\"".equals(literal)) {
        return true;
      }
    }

    return !(tokenType == GroovyTokenTypes.mGSTRING_CONTENT || tokenType == GroovyTokenTypes.mGSTRING_LITERAL || tokenType ==
                                                                                                                 GroovyTokenTypes.mSTRING_LITERAL || tokenType ==
                                                                                                                                                     GroovyTokenTypes.mGSTRING_END);
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();
    return tokenType == GroovyTokenTypes.mSTRING_LITERAL || tokenType == GroovyTokenTypes.mGSTRING_LITERAL;
  }

  @Override
  public CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (offset >= 3) {
      Document document = iterator.getDocument();
      if (document == null) return null;
      String quote = document.getText(new TextRange(offset - 3, offset));
      if ("'''".equals(quote)) return quote;
      if ("\"\"\"".equals(quote)) return quote;
    }
    if (offset >= 2) {
      Document document = iterator.getDocument();
      if (document == null) return null;
      String quote = document.getText(new TextRange(offset - 2, offset));
      if ("$/".equals(quote)) return "/$";
    }
    return null;
  }
}
