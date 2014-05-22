/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author ilyas
 */
public class GroovyReferenceCharFilter extends CharFilter {
  @Override
  @Nullable
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    final PsiFile psiFile = lookup.getPsiFile();
    if (psiFile != null && !psiFile.getViewProvider().getLanguages().contains(GroovyLanguage.INSTANCE)) return null;

    LookupElement item = lookup.getCurrentItem();
    if (item == null) return null;

    if (Character.isJavaIdentifierPart(c) || c == '\'') {
      return Result.ADD_TO_PREFIX;
    }

    int caret = lookup.getEditor().getCaretModel().getOffset();
    if (c == '.' && prefixLength == 0 && !lookup.isSelectionTouched() && caret > 0 &&
        lookup.getEditor().getDocument().getCharsSequence().charAt(caret - 1) == '.') {
      return Result.HIDE_LOOKUP;
    }

    if (c == ':') {
      PsiFile file = lookup.getPsiFile();
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(lookup.getEditor().getDocument());
      PsiElement element = file.findElementAt(Math.max(caret - 1, 0));
      if (PsiJavaPatterns.psiElement().withParent(
        PsiJavaPatterns.psiElement(GrReferenceExpression.class).withParent(
          StandardPatterns.or(PsiJavaPatterns.psiElement(GrCaseLabel.class),
                              PsiJavaPatterns.psiElement(GrConditionalExpression.class)))).accepts(element)) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      if (item.getObject() instanceof NamedArgumentDescriptor &&
          (MapArgumentCompletionProvider.IN_ARGUMENT_LIST_OF_CALL.accepts(element) ||
           MapArgumentCompletionProvider.IN_LABEL.accepts(element))) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      return Result.HIDE_LOOKUP;
    }


    if (c == '[' || c == ']' || c == ')' || c == '>') return CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '<' && item.getObject() instanceof PsiClass) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '(' && PsiKeyword.RETURN.equals(item.getLookupString())) {
      return Result.HIDE_LOOKUP;
    }

    return null;
  }

}
