/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[0];
    return InspectionGadgetsBundle.message(
      "cast.conflicts.with.instanceof.problem.descriptor",  referenceExpression.getText());
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(final Object... infos) {
    final String castExpressionType = ((PsiTypeElement)infos[1]).getText();
    final String instanceofType = ((PsiTypeElement)infos[2]).getText();
    return new InspectionGadgetsFix[]{
      new ReplaceCastFix(instanceofType, castExpressionType),
      new ReplaceInstanceofFix(instanceofType, castExpressionType)
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastConflictsWithInstanceofVisitor();
  }

  private static class CastConflictsWithInstanceofVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement castType = expression.getCastType();
      if (castType == null) {
        return;
      }
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (!(operand instanceof PsiReferenceExpression)) return;
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
      PsiType castType1 = castType.getType();
      PsiInstanceOfExpression conflictingInstanceof = InstanceOfUtils.getConflictingInstanceof(castType1, referenceExpression, expression);
      if (conflictingInstanceof == null) return;
      PsiTypeElement instanceofTypeElement = conflictingInstanceof.getCheckType();
      if (instanceofTypeElement == null) return;
      PsiType psiType = TypeConstraint.fromDfType(CommonDataflow.getDfType(operand)).getPsiType(operand.getProject());
      if (psiType != null && castType1.isAssignableFrom(psiType)) return;
      registerError(expression, referenceExpression, castType, instanceofTypeElement);
    }
  }

  private abstract static class ReplaceFix extends InspectionGadgetsFix {

    protected ReplaceFix() {
    }

    @Override
    protected final void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiTypeElement castTypeElement;
      final PsiReferenceExpression reference;
      if (element instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)element;
        castTypeElement = typeCastExpression.getCastType();
        final PsiExpression operand = typeCastExpression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
          return;
        }
        reference = (PsiReferenceExpression)operand;
      } else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)qualifier;
        castTypeElement = classObjectAccessExpression.getOperand();
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
          return;
        }
        final PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiReferenceExpression)) {
          return;
        }
        reference = (PsiReferenceExpression)argument;
      } else {
        return;
      }
      if (castTypeElement == null) {
        return;
      }
      final PsiInstanceOfExpression conflictingInstanceof =
        InstanceOfUtils.getConflictingInstanceof(castTypeElement.getType(), reference, element);
      if (conflictingInstanceof == null) {
        return;
      }
      final PsiTypeElement instanceofTypeElement = conflictingInstanceof.getCheckType();
      if (instanceofTypeElement == null) {
        return;
      }
      final PsiElement newElement = replace(castTypeElement, instanceofTypeElement);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      codeStyleManager.shortenClassReferences(newElement);
    }

    protected abstract PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement);
  }

  private static class ReplaceCastFix extends ReplaceFix {

    private final String myInstanceofType;
    private final String myCastType;

    ReplaceCastFix(String instanceofType, String castType) {
      myInstanceofType = instanceofType;
      myCastType = castType;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("cast.conflicts.with.instanceof.quickfix1", myCastType, myInstanceofType);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.cast.fix.family.name");
    }

    @Override
    protected PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement) {
      return castTypeElement.replace(instanceofTypeElement);
    }
  }

  private static class ReplaceInstanceofFix extends ReplaceFix {

    private final String myInstanceofType;
    private final String myCastType;

    ReplaceInstanceofFix(String instanceofType, String castType) {
      myInstanceofType = instanceofType;
      myCastType = castType;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.instanceof.fix.family.name");
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("cast.conflicts.with.instanceof.quickfix2", myInstanceofType, myCastType);
    }

    @Override
    protected PsiElement replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement) {
      return instanceofTypeElement.replace(castTypeElement);
    }
  }
}