// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public final class GroovyReferenceCharFilter extends CharFilter {
  @Override
  public @Nullable Result acceptChar(char c, int prefixLength, Lookup lookup) {
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
          PsiJavaPatterns.psiElement(GrConditionalExpression.class))).accepts(element)) {
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
    if (c == '(' && JavaKeywords.RETURN.equals(item.getLookupString())) {
      return Result.HIDE_LOOKUP;
    }

    return null;
  }

}
