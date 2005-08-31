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
import org.jetbrains.annotations.NotNull;

public class UnnecessarySuperConstructorInspection extends ExpressionInspection {

  private final UnnecessarySuperConstructorFix fix = new UnnecessarySuperConstructorFix();

  public String getID() {
    return "UnnecessaryCallToSuper";
  }

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarySuperConstructorVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class UnnecessarySuperConstructorFix extends InspectionGadgetsFix {
    public String getName() {
      return "Remove unnecessary super()";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement superCall = descriptor.getPsiElement();
      final PsiElement callStatement = superCall.getParent();
      assert callStatement != null;
      deleteElement(callStatement);
    }

  }

  private static class UnnecessarySuperConstructorVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final String methodText = methodExpression.getText();
      if (!PsiKeyword.SUPER.equals(methodText)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 0) {
        return;
      }
      registerError(call);
    }
  }
}
