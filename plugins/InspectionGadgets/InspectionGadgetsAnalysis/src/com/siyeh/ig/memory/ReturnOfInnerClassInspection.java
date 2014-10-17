/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.memory;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class ReturnOfInnerClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean ignoreNonPublic = false;

  private enum ClassType { ANONYMOUS_CLASS, LOCAL_CLASS, INNER_CLASS }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.of.inner.class.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    switch ((ClassType)infos[0]) {
      case ANONYMOUS_CLASS:
        return InspectionGadgetsBundle.message("return.of.anonymous.class.problem.descriptor");
      case LOCAL_CLASS: {
        final PsiClass aClass = (PsiClass)infos[1];
        return InspectionGadgetsBundle.message("return.of.local.class.problem.descriptor", aClass.getName());
      }
      case INNER_CLASS: {
        final PsiClass aClass = (PsiClass)infos[1];
        return InspectionGadgetsBundle.message("return.of.inner.class.problem.descriptor", aClass.getName());
      }
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("return.of.inner.class.ignore.non.public.option"),
                                          this, "ignoreNonPublic");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfInnerClassVisitor();
  }

  private  class ReturnOfInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression expression = ParenthesesUtils.stripParentheses(statement.getReturnValue());
      if (expression == null) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      else if (ignoreNonPublic &&
               (method.hasModifierProperty(PsiModifier.PROTECTED) || method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL))) {
        return;
      }
      if (expression instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)expression;
        final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
        if (anonymousClass != null) {
          registerStatementError(statement, ClassType.ANONYMOUS_CLASS);
          return;
        }
      }
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (PsiUtil.isLocalClass(aClass)) {
        registerStatementError(statement, ClassType.LOCAL_CLASS, aClass);
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null || aClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerStatementError(statement, ClassType.INNER_CLASS, aClass);
    }
  }
}
