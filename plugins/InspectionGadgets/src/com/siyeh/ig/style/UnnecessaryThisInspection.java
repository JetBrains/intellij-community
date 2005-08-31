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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryThisInspection extends ExpressionInspection {

  private final UnnecessaryThisFix fix = new UnnecessaryThisFix();

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryThisVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessaryThisFix extends InspectionGadgetsFix {
    public String getName() {
      return "Remove unnecessary 'this.'";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement thisToken = descriptor.getPsiElement();
      final PsiReferenceExpression thisExpression = (PsiReferenceExpression)thisToken.getParent();
      assert thisExpression != null;
      final String newExpression = thisExpression.getReferenceName();
      replaceExpression(thisExpression, newExpression);
    }

  }

  private static class UnnecessaryThisVisitor extends BaseInspectionVisitor {

    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiReferenceParameterList parameterList =
        expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      if (parameterList.getTypeArguments().length > 0) {
        return;
      }
      final PsiExpression qualifierExpression =
        expression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiThisExpression)) {
        return;
      }
      final PsiThisExpression thisExpression =
        (PsiThisExpression)qualifierExpression;
      if (thisExpression.getQualifier() != null) {
        return;
      }
      if (expression.getParent() instanceof PsiCallExpression) {
        registerError(qualifierExpression);  // method calls are always in error
        return;
      }
      final String varName = expression.getReferenceName();
      if (varName == null) {
        return;
      }
      if (!VariableSearchUtils.existsLocalOrParameter(varName,
                                                      expression)) {
        registerError(thisExpression);
      }
    }
  }
}
