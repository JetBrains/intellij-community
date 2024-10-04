/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage;
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreSyntax;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Class provides completion feature for {@link com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes#SYNTAX} element.
 */
@ApiStatus.Internal
public class SyntaxCompletionContributor extends CompletionContributor {
  @NotNull
  private static final List<LookupElementBuilder> SYNTAX_ELEMENTS = new ArrayList<>();

  static {
    for (Syntax syntax : Syntax.values()) {
      SYNTAX_ELEMENTS.add(LookupElementBuilder.create(StringUtil.toLowerCase(syntax.toString())));
    }
  }

  public SyntaxCompletionContributor() {
    extend(CompletionType.BASIC,
           StandardPatterns.instanceOf(PsiElement.class),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement current = parameters.getPosition();
               Language language = parameters.getOriginalFile().getLanguage();
               boolean isSyntaxSupported = language.isKindOf(IgnoreLanguage.INSTANCE) && ((IgnoreLanguage)language).isSyntaxSupported();

               if (isSyntaxSupported && current.getParent() instanceof IgnoreSyntax && current.getPrevSibling() != null) {
                 result.addAllElements(SYNTAX_ELEMENTS);
               }
             }
           }
    );
  }
}
