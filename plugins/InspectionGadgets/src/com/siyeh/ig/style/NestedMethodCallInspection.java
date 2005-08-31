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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NestedMethodCallInspection extends ExpressionInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreFieldInitializations = true;
  private final NestedMethodCallFix fix = new NestedMethodCallFix();

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore nested method calls in field initializers",
                                          this, "m_ignoreFieldInitializations");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NestedMethodCallVisitor();
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class NestedMethodCallFix extends InspectionGadgetsFix {
    public String getName() {
      return "Introduce variable";
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
      final RefactoringActionHandlerFactory factory = RefactoringActionHandlerFactory.getInstance();
      final RefactoringActionHandler introduceHandler =
        factory.createIntroduceVariableHandler();
      final PsiElement methodNameElement = descriptor.getPsiElement();
      final PsiElement methodExpression = methodNameElement.getParent();
      assert methodExpression != null;
      final PsiElement methodCallExpression = methodExpression.getParent();
      introduceHandler.invoke(project, new PsiElement[]{methodCallExpression}, null);
    }
  }

  private class NestedMethodCallVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiExpression outerExpression = expression;
      while (outerExpression != null && outerExpression.getParent() instanceof PsiExpression) {
        outerExpression = (PsiExpression)outerExpression.getParent();
      }
      if (outerExpression == null) {
        return;
      }
      final PsiElement parent = outerExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return;
      }
      if (grandParent instanceof PsiMethodCallExpression) {

        final PsiMethodCallExpression surroundingCall =
          (PsiMethodCallExpression)grandParent;
        final PsiReferenceExpression methodExpression =
          surroundingCall.getMethodExpression();
        final String callName = methodExpression.getReferenceName();
        if (PsiKeyword.THIS.equals(callName) || PsiKeyword.SUPER.equals(callName)) {
          return;     //ignore nested method calls at the start of a constructor,
          //where they can't be extracted
        }
      }
      final PsiReferenceExpression reference =
        expression.getMethodExpression();
      if (reference == null) {
        return;
      }
      if (m_ignoreFieldInitializations) {
        final PsiElement field =
          PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      registerMethodCallError(expression);
    }
  }
}
