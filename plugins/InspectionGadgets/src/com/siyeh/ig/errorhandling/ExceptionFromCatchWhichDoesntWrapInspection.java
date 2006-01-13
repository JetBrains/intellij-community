/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ExceptionFromCatchWhichDoesntWrapInspection extends StatementInspection {

  public String getID() {
    return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
  }

  public String getGroupDisplayName() {
    return GroupNames.ERRORHANDLING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionFromCatchWhichDoesntWrapVisitor();
  }

  private static class ExceptionFromCatchWhichDoesntWrapVisitor extends StatementInspectionVisitor {

    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      if (!ControlFlowUtils.isInCatchBlock(statement)) {
        return;
      }
      final PsiExpression exception = statement.getException();
      if (!(exception instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)exception;
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args == null) {
        return;
      }
      if (argIsCatchParameter(args)) {
        return;
      }
      registerStatementError(statement);
    }

    private static boolean argIsCatchParameter(PsiExpression[] args) {
      for (final PsiExpression arg : args) {
        if (arg instanceof PsiReferenceExpression) {
          final PsiReferenceExpression ref = (PsiReferenceExpression)arg;
          final PsiElement referent = ref.resolve();
          if (referent instanceof PsiParameter && ((PsiParameter)referent).getDeclarationScope() instanceof PsiCatchSection) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
