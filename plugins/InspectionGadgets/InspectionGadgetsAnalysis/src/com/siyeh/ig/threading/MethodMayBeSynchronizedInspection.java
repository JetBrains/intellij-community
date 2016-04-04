/*
 * Copyright 2006-2015 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodMayBeSynchronizedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.may.be.synchronized.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "method.may.be.synchronized.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MethodMayBeSynchronizedQuickFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodMayBeSynchronizedVisitor();
  }

  private static class MethodMayBeSynchronizedQuickFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "method.may.be.synchronized.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)identifier.getParent();
      final PsiCodeBlock methodBody = method.getBody();
      final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(methodBody);
      if (!(statement instanceof PsiSynchronizedStatement)) {
        return;
      }
      final PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)statement;
      final PsiCodeBlock body = synchronizedStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        final PsiElement added =
          methodBody.addRangeBefore(
            statements[0],
            statements[statements.length - 1],
            synchronizedStatement);
        final CodeStyleManager codeStyleManager =
          CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(added);
      }
      synchronizedStatement.delete();
      final PsiModifierList modifierList = method.getModifierList();
      modifierList.setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }
  }

  private static class MethodMayBeSynchronizedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)grandParent;
      if (!ControlFlowUtils.hasStatementCount(method.getBody(), 1)) {
        return;
      }
      final PsiExpression lockExpression = statement.getLockExpression();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        if (!(lockExpression instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        final PsiClassObjectAccessExpression classExpression =
          (PsiClassObjectAccessExpression)lockExpression;
        final PsiTypeElement typeElement =
          classExpression.getOperand();
        final PsiType type = typeElement.getType();
        if (!(type instanceof PsiClassType)) {
          return;
        }
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass aClass = classType.resolve();
        final PsiClass containingClass = method.getContainingClass();
        if (aClass != containingClass) {
          return;
        }
        registerMethodError(method);
      }
      else {
        if (!(lockExpression instanceof PsiThisExpression)) {
          return;
        }
        final PsiThisExpression thisExpression =
          (PsiThisExpression)lockExpression;
        final PsiJavaCodeReferenceElement qualifier =
          thisExpression.getQualifier();
        if (qualifier != null) {
          final PsiElement target = qualifier.resolve();
          final PsiClass containingClass = method.getContainingClass();
          if (!containingClass.equals(target)) {
            return;
          }
        }
        registerMethodError(method);
      }
    }
  }
}