// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class AtomicFieldUpdaterIssuesInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("atomic.field.updater.issues.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AtomicFieldUpdaterIssuesVisitor();
  }

  private static class AtomicFieldUpdaterIssuesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"newUpdater".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 2) {
        return;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final Object value = ExpressionUtils.computeConstantExpression(lastArgument);
      if (!(value instanceof String)) {
        return;
      }
      final String fieldName = (String)value;
      final PsiExpression firstArgument = ParenthesesUtils.stripParentheses(arguments[0]);
      if (!(firstArgument instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)firstArgument;
      final PsiType operandType = classObjectAccessExpression.getOperand().getType();
      if (!(operandType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)operandType;
      final PsiClass target = classType.resolve();
      if (target == null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final String typeString = TypeUtils.expressionHasTypeOrSubtype(expression,
                                                                     "java.util.concurrent.atomic.AtomicLongFieldUpdater",
                                                                     "java.util.concurrent.atomic.AtomicIntegerFieldUpdater",
                                                                     "java.util.concurrent.atomic.AtomicReferenceFieldUpdater");
      if (typeString == null) {
        return;
      }
      final PsiField field = target.findFieldByName(fieldName, false);
      if (field == null) {
        registerError(lastArgument,
                      InspectionGadgetsBundle.message("field.not.found.in.class.problem.descriptor", fieldName, target.getName()));
        return;
      }
      else if (typeString.equals("java.util.concurrent.atomic.AtomicLongFieldUpdater")) {
        if (arguments.length != 2) {
          return;
        }
        if (!PsiType.LONG.equals(field.getType())) {
          registerError(lastArgument, InspectionGadgetsBundle.message("field.incorrect.type.problem.descriptor", fieldName, "long"));
          return;
        }
      }
      else if (typeString.equals("java.util.concurrent.atomic.AtomicIntegerFieldUpdater")) {
        if (arguments.length != 2) {
          return;
        }
        if (!PsiType.INT.equals(field.getType())) {
          registerError(lastArgument, InspectionGadgetsBundle.message("field.incorrect.type.problem.descriptor", fieldName, "int"));
          return;
        }
      }
      else if (typeString.equals("java.util.concurrent.atomic.AtomicReferenceFieldUpdater")) {
        if (arguments.length != 3) {
          return;
        }
        final PsiExpression argument2 = arguments[1];
        if (!(argument2 instanceof PsiClassObjectAccessExpression)) {
          return;
        }
        final PsiClassObjectAccessExpression objectAccessExpression = (PsiClassObjectAccessExpression)argument2;
        final PsiType type = objectAccessExpression.getOperand().getType();
        final PsiType substFieldType = classType.resolveGenerics().getSubstitutor().substitute(field.getType());
        if (substFieldType == null) {
          return;
        }
        if (!substFieldType.isAssignableFrom(type)) {
          registerError(lastArgument, InspectionGadgetsBundle.message("field.incorrect.type.problem.descriptor",
                                                                      fieldName, type.getPresentableText()));
          return;
        }
      }
      else {
        assert false;
      }
      if (!field.hasModifierProperty(PsiModifier.VOLATILE)) {
        registerError(lastArgument, InspectionGadgetsBundle.message("field.missing.volatile.modifier.problem.descriptor", fieldName));
      }
      else if (field.hasModifierProperty(PsiModifier.STATIC)) {
        registerError(lastArgument, InspectionGadgetsBundle.message("field.has.static.modifier.problem.descriptor", fieldName));
      }
      else if (!field.hasModifierProperty(PsiModifier.PUBLIC) && ClassUtils.getContainingClass(expression) != field.getContainingClass()) {
        if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
          registerError(lastArgument, InspectionGadgetsBundle.message("private.field.not.accessible.problem.descriptor", fieldName));
        }
        else if (!ClassUtils.inSamePackage(expression, field)) {
          if (field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            registerError(lastArgument, InspectionGadgetsBundle.message("package.local.field.not.accessible", fieldName));
          }
          final PsiClass expressionClass = ClassUtils.getContainingClass(expression);
          final PsiClass fieldClass = field.getContainingClass();
          if (expressionClass != null && fieldClass != null && !expressionClass.isInheritor(fieldClass, true)) {
            if (field.hasModifierProperty(PsiModifier.PROTECTED)) {
              registerError(lastArgument, InspectionGadgetsBundle.message("protected.field.not.accessible.problem.descriptor", fieldName));
            }
          }
        }
      }
    }
  }
}