package org.jetbrains.javafx.editor;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.JavaFxElementType;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

/**
 * Brace matcher for JavaFx
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxBraceMatcher implements PairedBraceMatcher {
  private static final BracePair[] PAIRS = {new BracePair(JavaFxTokenTypes.LPAREN, JavaFxTokenTypes.RPAREN, false),
    new BracePair(JavaFxTokenTypes.LBRACK, JavaFxTokenTypes.RBRACK, false),
    new BracePair(JavaFxTokenTypes.LBRACE, JavaFxTokenTypes.RBRACE, true)};

  public BracePair[] getPairs() {
    return PAIRS;
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType braceType, @Nullable IElementType tokenType) {
    if (!(tokenType instanceof JavaFxElementType)) {
      return true;
    }
    return JavaFxTokenTypes.WHITESPACES.contains(tokenType) ||
           JavaFxTokenTypes.COMMENTS.contains(tokenType) ||
           tokenType == JavaFxTokenTypes.SEMICOLON ||
           tokenType == JavaFxTokenTypes.COMMA ||
           tokenType == JavaFxTokenTypes.RPAREN ||
           tokenType == JavaFxTokenTypes.RBRACK ||
           tokenType == JavaFxTokenTypes.RBRACE ||
           tokenType == JavaFxTokenTypes.LBRACE;
  }

  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
