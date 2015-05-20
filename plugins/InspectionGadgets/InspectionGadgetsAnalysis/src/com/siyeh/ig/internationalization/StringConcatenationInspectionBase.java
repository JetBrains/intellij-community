/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public class StringConcatenationInspectionBase extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAsserts = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSystemOuts = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSystemErrs = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreThrowableArguments = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreConstantInitializers = false;

  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility

  @SuppressWarnings("PublicField")
  public boolean ignoreInToString = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.problem.descriptor");
  }

  @Override
  @NotNull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[0];
    final Collection<InspectionGadgetsFix> result = new ArrayList();
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(polyadicExpression);
    if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)parent;
      final InspectionGadgetsFix fix = createAddAnnotationFix(variable);
      result.add(fix);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (lhs instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner) {
          final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
          final InspectionGadgetsFix fix = createAddAnnotationFix(modifierListOwner);
          result.add(fix);
        }
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression instanceof PsiReferenceExpression) {
          final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiModifierListOwner) {
            final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
            result.add(createAddAnnotationFix(modifierListOwner));
          }
        }
      }
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      final PsiModifierListOwner element1 = getAnnotatableElement(operand);
      if (element1 != null) {
        final InspectionGadgetsFix fix = createAddAnnotationFix(element1);
        result.add(fix);
      }
    }
    final PsiElement expressionParent = PsiTreeUtil.getParentOfType(polyadicExpression, PsiReturnStatement.class, PsiExpressionList.class);
    if (!(expressionParent instanceof PsiExpressionList) && expressionParent != null) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(expressionParent, PsiMethod.class);
      if (method != null) {
        final InspectionGadgetsFix fix = createAddAnnotationFix(method);
        result.add(fix);
      }
    }
    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  private static DelegatingFix createAddAnnotationFix(PsiModifierListOwner variable) {
    return new DelegatingFix(new AddAnnotationPsiFix(AnnotationUtil.NON_NLS, variable,PsiNameValuePair.EMPTY_ARRAY));
  }

  @Nullable
  public static PsiModifierListOwner getAnnotatableElement(PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement element = referenceExpression.resolve();
    if (!(element instanceof PsiModifierListOwner)) {
      return null;
    }
    return (PsiModifierListOwner)element;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.assert.option"), "ignoreAsserts");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.system.out.option"), "ignoreSystemOuts");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.system.err.option"), "ignoreSystemErrs");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.exceptions.option"), "ignoreThrowableArguments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("string.concatenation.ignore.constant.initializers.option"), "ignoreConstantInitializers");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.tostring"), "ignoreInToString");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationVisitor();
  }

  private class StringConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (NonNlsUtils.isNonNlsAnnotated(operand)) {
          return;
        }
      }
      if (AnnotationUtil.isInsideAnnotation(expression)) {
        return;
      }
      if (ignoreAsserts) {
        final PsiAssertStatement assertStatement =
          PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, true, PsiCodeBlock.class, PsiClass.class);
        if (assertStatement != null) {
          return;
        }
      }
      if (ignoreSystemErrs || ignoreSystemOuts) {
        final PsiMethodCallExpression methodCallExpression =
          PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
        if (methodCallExpression != null) {
          final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
          @NonNls
          final String canonicalText = methodExpression.getCanonicalText();
          if (ignoreSystemOuts && "System.out.println".equals(canonicalText) || "System.out.print".equals(canonicalText)) {
            return;
          }
          if (ignoreSystemErrs && "System.err.println".equals(canonicalText) || "System.err.print".equals(canonicalText)) {
            return;
          }
        }
      }
      if (ignoreThrowableArguments) {
        final PsiNewExpression newExpression =
          PsiTreeUtil.getParentOfType(expression, PsiNewExpression.class, true, PsiCodeBlock.class, PsiClass.class);
        if (newExpression != null) {
          final PsiType newExpressionType = newExpression.getType();
          if (InheritanceUtil.isInheritor(newExpressionType, "java.lang.Throwable")) {
            return;
          }
        } else {
          final PsiMethodCallExpression methodCallExpression =
            PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
          if (RefactoringChangeUtil.isSuperOrThisMethodCall(methodCallExpression)) {
            return;
          }
        }
      }
      if (ignoreConstantInitializers) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiBinaryExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiField) {
          final PsiField field = (PsiField)parent;
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
            return;
          }
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            return;
          }
        }
      }
      if (ignoreInToString) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (MethodUtils.isToString(method)) {
          return;
        }
      }
      if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
        return;
      }
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (!ExpressionUtils.isStringConcatenationOperand(operand)) {
          continue;
        }
        final PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        if (token == null) {
          continue;
        }
        registerError(token, expression);
      }
    }
  }
}
