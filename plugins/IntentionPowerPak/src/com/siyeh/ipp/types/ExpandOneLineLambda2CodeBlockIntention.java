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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExpandOneLineLambda2CodeBlockIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#" + ExpandOneLineLambda2CodeBlockIntention.class.getName());
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new LambdaExpressionPredicate();
  }

  @NotNull
  @Override
  public String getText() {
    return "Expand lambda expression body to {...}";
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    RefactoringUtil.expandExpressionLambdaToCodeBlock(element);
  }


  private static class LambdaExpressionPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      return lambdaExpression != null && lambdaExpression.getBody() instanceof PsiExpression;
    }
  }
}
