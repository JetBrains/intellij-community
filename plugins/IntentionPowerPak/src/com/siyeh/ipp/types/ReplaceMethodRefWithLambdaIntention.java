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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceMethodRefWithLambdaIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceMethodRefWithLambdaIntention.class.getName());

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MethodRefPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {}

  @Override
  protected void processIntention(final Editor editor, @NotNull PsiElement element) {
    final PsiMethodReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class);
    LOG.assertTrue(referenceExpression != null);
    final PsiLambdaExpression expr = LambdaRefactoringUtil.convertMethodReferenceToLambda(referenceExpression, false, true);
    final Runnable runnable = new Runnable() {
      public void run() {
        LambdaRefactoringUtil.removeSideEffectsFromLambdaBody(editor, expr);
      }
    };
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      runnable.run();
    } else {
      application.invokeLater(runnable);
    }
  }

  private static class MethodRefPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final PsiMethodReferenceExpression methodReferenceExpression = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class);
      if (methodReferenceExpression != null) {
        final PsiType interfaceType = methodReferenceExpression.getFunctionalInterfaceType();
        if (interfaceType != null &&
            LambdaUtil.getFunctionalInterfaceMethod(interfaceType) != null &&
            methodReferenceExpression.resolve() != null) {
          return true;
        }
      }
      return false;
    }
  }
}
