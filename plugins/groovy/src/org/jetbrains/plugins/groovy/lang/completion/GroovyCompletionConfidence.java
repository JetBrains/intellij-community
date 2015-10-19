/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import static org.jetbrains.plugins.groovy.shell.GroovyShellRunnerImpl.GROOVY_SHELL_FILE;

/**
 * @author peter
 */
public class GroovyCompletionConfidence extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS && psiFile.getUserData(GROOVY_SHELL_FILE) == Boolean.TRUE) {
      return ThreeState.YES;
    }

    if (PsiImplUtil.isLeafElementOfType(contextElement, TokenSets.STRING_LITERALS)) {
      @SuppressWarnings("ConstantConditions")
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

    return ThreeState.UNSURE;
  }
}
