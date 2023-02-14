// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

public class GroovyCompletionConfidence extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (PsiImplUtil.isLeafElementOfType(contextElement, TokenSets.STRING_LITERALS)) {
      PsiElement parent = contextElement.getParent();
      if (parent != null) {
        for (PsiReference reference : parent.getReferences()) {
          if (!reference.isSoft() && reference.getRangeInElement().shiftRight(parent.getTextOffset()).containsOffset(offset)) {
            return ThreeState.NO;
          }
        }
      }

      return ThreeState.YES;
    }

    if (PsiJavaPatterns.psiElement().afterLeaf("def").accepts(contextElement)) {
      return ThreeState.YES;
    }
    if (contextElement.textMatches("..") || contextElement.textMatches("...")) {
      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }
}
