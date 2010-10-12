package org.jetbrains.javafx.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxQuoteHandler extends SimpleTokenSetQuoteHandler implements JavaLikeQuoteHandler {
  public JavaFxQuoteHandler() {
    super(JavaFxTokenTypes.STRINGS);
  }

  public TokenSet getConcatenatableStringTokenTypes() {
    return JavaFxTokenTypes.STRINGS;
  }

  public String getStringConcatenationOperatorRepresentation() {
    return "";
  }

  public TokenSet getStringTokenTypes() {
    return myLiteralTokenSet;
  }

  public boolean isAppropriateElementTypeForLiteral(@NotNull IElementType tokenType) {
    return JavaFxTokenTypes.WHITESPACES.contains(tokenType) ||
           JavaFxTokenTypes.COMMENTS.contains(tokenType) ||
           JavaFxTokenTypes.ALL_STRINGS.contains(tokenType) ||
           tokenType == JavaFxTokenTypes.SEMICOLON ||
           tokenType == JavaFxTokenTypes.COMMA ||
           tokenType == JavaFxTokenTypes.RPAREN ||
           tokenType == JavaFxTokenTypes.RBRACK ||
           tokenType == JavaFxTokenTypes.RBRACE;
  }

  public boolean needParenthesesAroundConcatenation(PsiElement element) {
    return false;
  }
}
