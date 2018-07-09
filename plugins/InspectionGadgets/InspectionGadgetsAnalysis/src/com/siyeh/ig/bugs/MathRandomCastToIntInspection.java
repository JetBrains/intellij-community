/*
 * Copyright 2011-2018 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MathRandomCastToIntInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("math.random.cast.to.int.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[1];
    return InspectionGadgetsBundle.message("math.random.cast.to.int.problem.descriptor", type.getPresentableText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTypeCastExpression expression = (PsiTypeCastExpression)infos[0];
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return null;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (JavaTokenType.ASTERISK != tokenType || polyadicExpression.getType() == null) {
      return null;
    }
    return new MathRandomCastToIntegerFix();
  }

  private static class MathRandomCastToIntegerFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("math.random.cast.to.int.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      while (parent instanceof PsiPrefixExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
      final PsiElement grandParent = typeCastExpression.getParent();
      if (!(grandParent instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)grandParent;
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = polyadicExpression.getType();
      if (type == null) {
        return;
      }
      @NonNls final StringBuilder newExpression = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      newExpression.append("(").append(type.getCanonicalText()).append(")(");
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (final PsiExpression expression : operands) {
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(expression);
        if (token != null) {
          newExpression.append(token.getText());
        }
        if (typeCastExpression.equals(expression)) {
          newExpression.append(commentTracker.text(operand));
        }
        else {
          newExpression.append(commentTracker.text(expression));
        }
      }
      newExpression.append(')');
      PsiReplacementUtil.replaceExpression(polyadicExpression, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MathRandomCastToIntegerVisitor();
  }

  private static class MathRandomCastToIntegerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      PsiExpression operand = expression.getOperand();
      while (operand instanceof PsiPrefixExpression) {
        operand = ((PsiPrefixExpression)operand).getOperand();
      }
      if (!(operand instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiTypeElement castType = expression.getCastType();
      if (castType == null) {
        return;
      }
      final PsiType type = castType.getType();
      if (!(type instanceof PsiPrimitiveType) || PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type) || PsiType.BOOLEAN.equals(type)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)operand;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"random".equals(referenceName)) {
        return;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(qualifiedName) && !CommonClassNames.JAVA_LANG_STRICT_MATH.equals(qualifiedName)) {
        return;
      }
      registerError(methodCallExpression, expression, type);
    }
  }
}
