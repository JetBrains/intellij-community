/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
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
