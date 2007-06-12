package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ven
 */
public class GroovyQuoteHandler implements TypedHandler.QuoteHandler {

  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL ||
        tokenType == GroovyTokenTypes.mGSTRING_LITERAL){
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }
    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == GroovyTokenTypes.mWRONG_GSTRING_LITERAL ||
        tokenType == GroovyTokenTypes.mWRONG_STRING_LITERAL){
      int start = iterator.getStart();
      return offset == start;
    }
    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    return true;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();
    return tokenType == GroovyTokenTypes.mSTRING_LITERAL ||
           tokenType == GroovyTokenTypes.mGSTRING_LITERAL;
  }
}
