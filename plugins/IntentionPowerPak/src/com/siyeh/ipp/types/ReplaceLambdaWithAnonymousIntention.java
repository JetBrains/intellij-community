/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ipp.types;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplaceLambdaWithAnonymousIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceLambdaWithAnonymousIntention.class.getName());

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new LambdaPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
  }

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
    LOG.assertTrue(lambdaExpression != null);
    PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
    LOG.assertTrue(functionalInterfaceType != null);
    functionalInterfaceType = GenericsUtil.eliminateWildcards(functionalInterfaceType);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    LOG.assertTrue(method != null);

    final String blockText = getBodyText(lambdaExpression);
    if (blockText == null) return;

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(element.getProject());
    PsiNewExpression newExpression = (PsiNewExpression)psiElementFactory.createExpressionFromText("new " + functionalInterfaceType.getCanonicalText() + "(){}", lambdaExpression);
    newExpression = (PsiNewExpression)lambdaExpression.replace(newExpression);

    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    LOG.assertTrue(anonymousClass != null);
    final List<PsiGenerationInfo<PsiMethod>> infos = OverrideImplementUtil.overrideOrImplement(anonymousClass, method);
    if (infos != null && infos.size() == 1) {
      final PsiMethod member = infos.get(0).getPsiMember();
      PsiCodeBlock codeBlock = member.getBody();
      LOG.assertTrue(codeBlock != null);
      codeBlock = (PsiCodeBlock)codeBlock.replace(psiElementFactory.createCodeBlockFromText(blockText, null));
      final Set<PsiVariable> vars2BeFinal = new HashSet<PsiVariable>();
      codeBlock.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolve = expression.resolve();
          if (resolve instanceof PsiVariable) {
            final PsiVariable variable = (PsiVariable)resolve;
            final PsiClass innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, expression);
            if (innerClass != null) {
              vars2BeFinal.add(variable);
            }
          }
        }
      });
      for (PsiVariable var : vars2BeFinal) {
        PsiUtil.setModifierProperty(var, PsiModifier.FINAL, true);
      }
      GenerateMembersUtil.positionCaret(editor, member, true);
    }
  }

  private static String getBodyText(PsiLambdaExpression lambdaExpression) {
    String blockText;
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression) {
      blockText = "{";
      blockText += ((PsiExpression)body).getType() == PsiType.VOID ? "" : "return ";
      blockText +=  body.getText() + ";}";
    } else if (body != null) {
      blockText = body.getText();
    } else {
      blockText = null;
    }
    return blockText;
  }

  private static class LambdaPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression != null && PsiTreeUtil.isAncestor(lambdaExpression.getParameterList(), element, false)) {
        final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
        return functionalInterfaceType != null && LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null && LambdaUtil.isLambdaFullyInferred(lambdaExpression, functionalInterfaceType);
      }
      return false;
    }
  }
}
