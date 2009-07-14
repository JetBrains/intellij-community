package com.intellij.spellchecker;

import com.intellij.psi.PsiIdentifier;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class PsiIdentifierTokenizer extends Tokenizer<PsiIdentifier> {

   @Nullable
   @Override
  public Token[] tokenize(@NotNull PsiIdentifier element) {
    return element.getText()==null?null:new Token[]{new Token<PsiIdentifier>(element,element.getText(),true)};
  }
}