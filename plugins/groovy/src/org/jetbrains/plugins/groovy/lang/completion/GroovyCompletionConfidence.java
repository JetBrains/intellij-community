/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.console.GroovyShellAction;
import org.jetbrains.plugins.groovy.console.GroovyShellActionBase;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class GroovyCompletionConfidence extends CompletionConfidence {
  private static final ElementPattern<PsiElement> CLOSURE_LBRACE = psiElement().withText("{").withParent(GrClosableBlock.class);

  private static boolean isPossibleClosureParameter(GrReferenceExpression ref) {
    return psiElement().afterLeaf(CLOSURE_LBRACE).accepts(ref) ||
           psiElement().afterLeaf(
             psiElement().afterLeaf(",").withParent(
               psiElement(GrVariable.class).withParent(
                 psiElement(GrVariableDeclaration.class).afterLeaf(CLOSURE_LBRACE)))).accepts(ref) ||
           GroovyCompletionContributor.isInPossibleClosureParameter(ref);
  }

  @NotNull
  @Override
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();

    PsiFile file = position.getContainingFile();
    if (file instanceof GroovyFile && GroovyScriptTypeDetector.getScriptType((GroovyFile)file) != GroovyScriptTypeDetector.DEFAULT_TYPE) {
      return ThreeState.NO;
    }

    if (position.getParent() instanceof GrReferenceElement &&
        psiElement().afterLeaf(psiElement().withText("(").withParent(GrForStatement.class)).accepts(position)) {
      return ThreeState.NO;
    }

    if (position.getParent() instanceof GrReferenceExpression) {
      final GrReferenceExpression ref = (GrReferenceExpression)position.getParent();
      final GrExpression qualifier = ref.getQualifierExpression();
      if (qualifier == null) {
        if (isPossibleClosureParameter(ref)) return ThreeState.NO;
        if (parameters.getOriginalFile().getUserData(GroovyShellActionBase.GROOVY_SHELL_FILE) == Boolean.TRUE) {
          return ThreeState.NO;
        }

        GrExpression runtimeQualifier = PsiImplUtil.getRuntimeQualifier(ref);
        if (runtimeQualifier != null && runtimeQualifier.getType() == null) {
          return ThreeState.NO;
        }

        return ThreeState.YES;
      }

      if (qualifier.getType() == null) {
        return ThreeState.NO;
      }
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS && psiFile.getUserData(GroovyShellAction.GROOVY_SHELL_FILE) == Boolean.TRUE) {
      return ThreeState.YES;
    }

    if (com.intellij.psi.impl.PsiImplUtil.isLeafElementOfType(contextElement, TokenSets.STRING_LITERALS)) {
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

    if (psiElement().afterLeaf("def").accepts(contextElement)) {
      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }
}
