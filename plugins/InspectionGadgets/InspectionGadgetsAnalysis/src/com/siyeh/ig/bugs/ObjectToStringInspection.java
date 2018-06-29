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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.internationalization.NonNlsUtils;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ObjectToStringInspection extends BaseInspection {
  public boolean IGNORE_NONNLS = false;
  public boolean IGNORE_EXCEPTION = false;
  public boolean IGNORE_ASSERT = false;
  public boolean IGNORE_TOSTRING = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.option.ignore.nonnls"), "IGNORE_NONNLS");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.option.ignore.exceptions"), "IGNORE_EXCEPTION");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.option.ignore.assert"), "IGNORE_ASSERT");
    panel.addCheckbox(InspectionGadgetsBundle.message("inspection.option.ignore.in.tostring"), "IGNORE_TOSTRING");
    return panel;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("default.tostring.call.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("default.tostring.call.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectToStringVisitor();
  }

  private class ObjectToStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        checkExpression(operand);
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      if (!ExpressionUtils.hasStringType(lhs)) {
        return;
      }
      final PsiExpression rhs = expression.getRExpression();
      checkExpression(rhs);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.TO_STRING.equals(name)) {
        if (!expression.getArgumentList().isEmpty()) {
          return;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        checkExpression(qualifier);
      }
      else if ("append".equals(name)) {
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
          return;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
          return;
        }
        final PsiExpression argument = arguments[0];
        checkExpression(argument);
      }
      else if ("valueOf".equals(name)) {
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
        final String canonicalText = referenceExpression.getCanonicalText();
        if (!CommonClassNames.JAVA_LANG_STRING.equals(canonicalText)) {
          return;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
          return;
        }
        final PsiExpression argument = arguments[0];
        checkExpression(argument);
      }
    }

    private void checkExpression(PsiExpression expression) {
      if (expression == null) return;

      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) return;

      if (IGNORE_TOSTRING && MethodUtils.isToString(PsiTreeUtil.getParentOfType(expression, PsiMethod.class))) return;

      if (IGNORE_EXCEPTION && (
        ExceptionUtils.isExceptionArgument(expression) ||
        PsiTreeUtil.getParentOfType(expression, PsiThrowStatement.class, true, PsiCodeBlock.class, PsiClass.class) != null)) return;

      if (IGNORE_ASSERT &&
          PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, true, PsiCodeBlock.class, PsiClass.class) != null) {
        return;
      }

      if (IGNORE_NONNLS && NonNlsUtils.isNonNlsAnnotatedUse(expression)) return;

      final PsiClassType classType = (PsiClassType)type;
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return;

      final PsiClass referencedClass = classType.resolve();
      if (referencedClass == null || referencedClass instanceof PsiTypeParameter) return;
      if (referencedClass.isEnum() || referencedClass.isInterface()) return;
      if (referencedClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(expression instanceof PsiSuperExpression)) return;
      if (hasGoodToString(referencedClass)) return;

      registerError(expression);
    }

    private boolean hasGoodToString(PsiClass aClass) {
      final PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.TO_STRING, true);
      for (PsiMethod method : methods) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          continue;
        }
        final String name = containingClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(name)) {
          continue;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }
}