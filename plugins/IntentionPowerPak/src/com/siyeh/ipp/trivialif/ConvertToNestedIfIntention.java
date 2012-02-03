/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 2/2/12
 */
public class ConvertToNestedIfIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {

      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiJavaToken)) {
          return false;
        }
        final PsiStatement containingStatement =
          PsiTreeUtil.getParentOfType(element,
                                      PsiStatement.class);
        if (containingStatement instanceof PsiReturnStatement) {
          final PsiExpression returnValue = ((PsiReturnStatement)containingStatement).getReturnValue();
          return returnValue instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)returnValue).getOperationTokenType() == JavaTokenType.ANDAND;
        }
        return false;
      }
    };
  }

  @Override
  public void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiStatement containingStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (containingStatement == null) {
      return;
    }
    final PsiReturnStatement returnStatement =
      (PsiReturnStatement)containingStatement;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return;
    }
    if (ErrorUtil.containsDeepError(returnValue)) {
      return;
    }
    final StringBuilder buf = new StringBuilder();
    if (returnValue instanceof PsiPolyadicExpression) {
      final PsiExpression[] operands = ((PsiPolyadicExpression)returnValue).getOperands();
      for (PsiExpression operand : operands) {
        buf.append("if (" + operand.getText() + ") {");
      }
      buf.append("return true;");
      for (PsiExpression operand : operands) {
        buf.append("}");
      }
    }

    final PsiStatement ifStmt =
      JavaPsiFacade.getElementFactory(containingStatement.getProject()).createStatementFromText(buf.toString(), containingStatement);
    CodeStyleManager.getInstance(containingStatement.getProject()).reformat(containingStatement.getParent().addBefore(ifStmt, containingStatement));
    replaceStatement("return false;", containingStatement);
  }
}
