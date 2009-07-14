package com.intellij.spellchecker;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class JavaSpellcheckingStrategy extends SpellcheckingStrategy {

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiMethod) return new MethodNameTokenizerJava();
    if (element instanceof PsiDocComment) return new DocCommentTokenizer();
    if (element instanceof PsiLiteralExpression) return
      new LiteralExpressionTokenizer();
    if (element instanceof PsiNamedElement)
      return new NamedElementTokenizer();
    return super.getTokenizer(element);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return StdLanguages.JAVA;
  }
}
