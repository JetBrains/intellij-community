/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EqualsToEqualityFix extends InspectionGadgetsFix {

  private final boolean myNegated;

  public EqualsToEqualityFix(boolean negated) {
    myNegated = negated;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return myNegated
           ? InspectionGadgetsBundle.message("not.equals.to.equality.quickfix")
           : InspectionGadgetsBundle.message("equals.to.equality.quickfix");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiMethodCallExpression call = (PsiMethodCallExpression)descriptor.getPsiElement().getParent().getParent();
    if (call == null) {
      return;
    }
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression lhs = PsiUtil.deparenthesizeExpression(methodExpression.getQualifierExpression());
    if (lhs == null) {
      return;
    }
    final PsiExpression rhs = PsiUtil.deparenthesizeExpression(call.getArgumentList().getExpressions()[0]);
    if (rhs == null) {
      return;
    }
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(call);
    if (parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent)) {
      PsiReplacementUtil.replaceExpression((PsiExpression)parent, getText(lhs) + "!=" + getText(rhs));
    }
    else {
      PsiReplacementUtil.replaceExpression(call, getText(lhs) + "==" + getText(rhs));
    }
  }

  private static String getText(PsiExpression rhs) {
    return ParenthesesUtils.getPrecedence(rhs) > ParenthesesUtils.EQUALITY_PRECEDENCE ? '(' + rhs.getText() + ')' : rhs.getText();
  }
}
