package com.intellij.spellchecker;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class LiteralExpressionTokenizer extends Tokenizer<PsiLiteralExpression> {

  @Override
  @Nullable
  public Token[] tokenize(@NotNull PsiLiteralExpression element) {
    return new Token[]{new Token<PsiLiteralExpression>(element, element.getText(), false)};
  }
}
